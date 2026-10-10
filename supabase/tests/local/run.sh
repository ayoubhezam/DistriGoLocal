#!/usr/bin/env bash
# Runs the migrations and their tests on a throwaway PostgreSQL cluster: no Supabase, no Docker.
#
#   supabase/tests/local/run.sh [work-dir]
#
# PG_BIN is PostgreSQL's bin folder (default: PostgreSQL 17 on Windows), work-dir where the cluster lives
# while it runs (default: a new temporary folder). The cluster is stopped and deleted at the end.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
supabase="$(cd "$here/../.." && pwd)"
PG_BIN="${PG_BIN:-/c/Program Files/PostgreSQL/17/bin}"
work="${1:-$(mktemp -d)}"
port="${PGPORT_TEST:-54329}"
data="$work/pgdata"

rm -rf "$data"
mkdir -p "$work"
"$PG_BIN/initdb" -D "$data" -U postgres -A trust -E UTF8 --no-locale >/dev/null
"$PG_BIN/pg_ctl" -D "$data" -o "-p $port" -l "$work/postgres.log" -w start >/dev/null
trap '"$PG_BIN/pg_ctl" -D "$data" -m fast -w stop >/dev/null; rm -rf "$data"' EXIT

sql() { "$PG_BIN/psql" -X -q -v ON_ERROR_STOP=1 -h localhost -p "$port" -U postgres -d postgres "$@"; }

sql -f "$here/supabase_stub.sql"
for migration in "$supabase"/migrations/*.sql; do
    sql -f "$migration"
    echo "applied $(basename "$migration")"
done

# The tests print "ok: …" for each check; the first failure stops them.
output="$(sql -f "$here/license_test.sql" 2>&1)" || { echo "$output"; exit 1; }
echo "$output" | grep -o 'ok: .*'
checks=$(echo "$output" | grep -c 'ok: ')

# ── The last seat, raced for by two phones at once ──
# Phone 1's activation holds its transaction open; phone 2 asks for the seat meanwhile. The subscription's row
# lock must make phone 2 wait and then see the seat taken, never give both a seat.
sql <<'SQL'
insert into auth.users (id) values ('eeeeeeee-0000-4000-8000-000000000005');
select private.create_business('eeeeeeee-0000-4000-8000-000000000005', 'Distribution E', 'phone-e');
create schema race;
create function race.activate(p_installation uuid) returns jsonb language sql as $$
    select private.activate_device('eeeeeeee-0000-4000-8000-000000000005', null, p_installation, p_installation::text,
                                   '\x00', null, 'SM-M346B', '16', '1.0', '{}', 'normal', false)
$$;
SQL

sql -At <<'SQL' >"$work/race-1.txt" &
begin;
select race.activate('e0000000-0000-4000-8000-0000000000e1') ->> 'status';
select pg_sleep(1.5);
commit;
SQL
first=$!
second=$(sql -At -c "select pg_sleep(0.3)" -c "select race.activate('e0000000-0000-4000-8000-0000000000e2') ->> 'status'" | tail -1)
wait $first
first_status=$(grep -v '^$' "$work/race-1.txt" | head -1)
active=$(sql -At -c "select count(*) from public.devices d join public.memberships m using (business_id)
                     where m.user_id = 'eeeeeeee-0000-4000-8000-000000000005' and d.status = 'active'")
if [[ "$first_status" != "activated" || "$second" != "seat_taken" || "$active" != "1" ]]; then
    echo "FAILED: the race for the last seat gave '$first_status' and '$second', $active active"
    exit 1
fi
echo "ok: two phones racing for the last seat: the first gets it, the second waits and is told it is taken"
checks=$((checks + 1))
echo "$checks SQL checks passed"

# ── The Edge Functions' code (Node 24 runs the TypeScript as is), against the same database ──
tests="$supabase/functions/tests"
[[ -d "$tests/node_modules" ]] || (cd "$tests" && npm ci --no-audit --no-fund >/dev/null)
DISTRIGO_TEST_DB_URL="postgres://postgres@localhost:$port/postgres" \
    node --test --test-reporter=spec "$tests"/*.test.ts
