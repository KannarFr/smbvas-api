# --- !Ups

create table user(
  id uuid primary key,
  email text,
  "password" text
);

create table resource(
  id uuid primary key,
  "type" text,
  label text,
  "description" text,
  color text,
  lat double precision,
  lng double precision,
  "date" timestamptz,
  "url" text,
  size double precision,
  creation_date timestamptz,
  edition_date timestamptz,
  deletion_date timestamptz,
  validator uuid references user(id)
);

# --- !Downs

drop table resource;
drop table user;
