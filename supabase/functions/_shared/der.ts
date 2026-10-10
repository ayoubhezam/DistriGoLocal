// A minimal DER reader: enough for X.509 certificates and Android's KeyDescription, and nothing more.
//
// Written here rather than taken from a library so that the same file runs on Supabase (Deno) and in the Node
// tests, and so that a tag a newer KeyMint adds is skipped rather than failing a schema.

export class DerError extends Error {}

export const UNIVERSAL = 0;
export const CONTEXT = 2;

/** One element: its tag, and where its content lies in the source. */
export interface Der {
    cls: number;
    constructed: boolean;
    tag: number;
    /** The whole element: identifier, length and content. */
    bytes: Uint8Array;
    content: Uint8Array;
}

/** The element at [offset] of [source]. */
export function read(source: Uint8Array, offset = 0): Der {
    let at = offset;
    const need = (count: number) => {
        if (at + count > source.length) throw new DerError("truncated");
    };
    need(1);
    const first = source[at++];
    const cls = first >> 6;
    const constructed = (first & 0x20) !== 0;
    let tag = first & 0x1f;
    if (tag === 0x1f) {
        // High tag number: base-128, seven bits a byte, the last without its top bit.
        tag = 0;
        for (;;) {
            need(1);
            const b = source[at++];
            tag = tag * 128 + (b & 0x7f);
            if (tag > 0x1fffff) throw new DerError("tag too large");
            if ((b & 0x80) === 0) break;
        }
    }
    need(1);
    let length = source[at++];
    if (length & 0x80) {
        const count = length & 0x7f;
        if (count === 0) throw new DerError("indefinite length");
        if (count > 4) throw new DerError("length too large");
        need(count);
        length = 0;
        for (let i = 0; i < count; i++) length = length * 256 + source[at++];
    }
    need(length);
    return {
        cls,
        constructed,
        tag,
        bytes: source.subarray(offset, at + length),
        content: source.subarray(at, at + length),
    };
}

/** The element filling [source] exactly. */
export function readWhole(source: Uint8Array): Der {
    const element = read(source);
    if (element.bytes.length !== source.length) throw new DerError("trailing bytes");
    return element;
}

/** The elements inside a constructed one. */
export function children(element: Der): Der[] {
    if (!element.constructed) throw new DerError("not constructed");
    const out: Der[] = [];
    let at = 0;
    while (at < element.content.length) {
        const child = read(element.content, at);
        out.push(child);
        at += child.bytes.length;
    }
    return out;
}

export function expect(element: Der | undefined, cls: number, tag: number, what: string): Der {
    if (!element || element.cls !== cls || element.tag !== tag) throw new DerError(`expected ${what}`);
    return element;
}

export const sequence = (e: Der | undefined, what = "SEQUENCE") => expect(e, UNIVERSAL, 0x10, what);

/** An INTEGER's value as a number; refuses values a double cannot hold exactly. */
export function integer(element: Der | undefined): number {
    const e = expect(element, UNIVERSAL, 0x02, "INTEGER");
    if (e.content.length === 0 || e.content.length > 6) throw new DerError("INTEGER out of range");
    let value = e.content[0] & 0x80 ? -1 : 0;
    for (const b of e.content) value = value * 256 + b;
    return value;
}

/** An INTEGER's magnitude in lowercase hex without leading zeros: how certificate serials are listed. */
export function integerHex(element: Der | undefined): string {
    const e = expect(element, UNIVERSAL, 0x02, "INTEGER");
    const hex = [...e.content].map((b) => b.toString(16).padStart(2, "0")).join("").replace(/^0+/, "");
    return hex === "" ? "0" : hex;
}

export function enumerated(element: Der | undefined): number {
    const e = expect(element, UNIVERSAL, 0x0a, "ENUMERATED");
    if (e.content.length !== 1) throw new DerError("ENUMERATED out of range");
    return e.content[0];
}

export function boolean(element: Der | undefined): boolean {
    const e = expect(element, UNIVERSAL, 0x01, "BOOLEAN");
    if (e.content.length !== 1) throw new DerError("bad BOOLEAN");
    return e.content[0] !== 0;
}

export function octetString(element: Der | undefined): Uint8Array {
    return expect(element, UNIVERSAL, 0x04, "OCTET STRING").content;
}

/** A BIT STRING's bits, which must be whole bytes. */
export function bitString(element: Der | undefined): Uint8Array {
    const e = expect(element, UNIVERSAL, 0x03, "BIT STRING");
    if (e.content.length === 0 || e.content[0] !== 0) throw new DerError("BIT STRING with unused bits");
    return e.content.subarray(1);
}

export function oid(element: Der | undefined): string {
    const e = expect(element, UNIVERSAL, 0x06, "OBJECT IDENTIFIER");
    if (e.content.length === 0) throw new DerError("empty OID");
    const parts: number[] = [];
    let value = 0;
    for (const b of e.content) {
        value = value * 128 + (b & 0x7f);
        if ((b & 0x80) === 0) {
            parts.push(value);
            value = 0;
        }
    }
    const first = parts.shift()!;
    const head = first < 80 ? [Math.floor(first / 40), first % 40] : [2, first - 80];
    return [...head, ...parts].join(".");
}

/** UTCTime or GeneralizedTime, as DER writes them: whole seconds, in UTC. */
export function time(element: Der | undefined): Date {
    if (!element || element.cls !== UNIVERSAL || (element.tag !== 0x17 && element.tag !== 0x18)) {
        throw new DerError("expected a time");
    }
    const text = new TextDecoder().decode(element.content);
    const match = element.tag === 0x17
        ? /^(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})Z$/.exec(text)
        : /^(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})Z$/.exec(text);
    if (!match) throw new DerError(`bad time ${text}`);
    let year = Number(match[1]);
    if (element.tag === 0x17) year += year >= 50 ? 1900 : 2000;
    return new Date(Date.UTC(year, Number(match[2]) - 1, Number(match[3]), Number(match[4]), Number(match[5]), Number(match[6])));
}
