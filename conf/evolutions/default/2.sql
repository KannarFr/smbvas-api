# --- !Ups

create table "analytics"(
  ip text primary key,
  last_access timestamptz,
  nb_access_to_map int
);

# --- !Downs

drop table "analytics";
