# --- !Ups

alter table resource add column provider_contact text;
alter table resource add column status text;

# --- !Downs

alter table resource drop column provider_contact;
alter table resource drop column status;
