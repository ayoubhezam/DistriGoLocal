-- The parts of a Supabase database the migrations rely on, for testing them on a plain PostgreSQL.
-- Not a migration: Supabase provides all of this itself.

create role anon nologin noinherit;
create role authenticated nologin noinherit;
create role service_role nologin noinherit bypassrls;

create schema auth;
create table auth.users (
    id                  uuid primary key,
    email               text,
    raw_user_meta_data  jsonb not null default '{}',
    created_at          timestamptz not null default now()
);

-- As Supabase defines it: the subject of the request's JWT.
create function auth.uid() returns uuid
language sql stable as $$
    select nullif(coalesce(current_setting('request.jwt.claim.sub', true),
                           nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'sub'), '')::uuid
$$;
grant usage on schema auth to anon, authenticated, service_role;

create schema extensions;
create extension pgcrypto with schema extensions;
grant usage on schema extensions to anon, authenticated, service_role;

-- Supabase's defaults: the API roles get everything in public. The migration narrows them; the tests check it did.
grant usage on schema public to anon, authenticated, service_role;
alter default privileges in schema public grant all on tables to anon, authenticated, service_role;
alter default privileges in schema public grant all on functions to anon, authenticated, service_role;
