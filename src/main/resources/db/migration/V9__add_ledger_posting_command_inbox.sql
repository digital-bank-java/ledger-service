create table ledger_posting_command_inbox (
    event_id uuid not null,
    event_type varchar(100) not null,
    topic varchar(255) not null,
    aggregate_id varchar(100) not null,
    posting_request_id varchar(100) not null,
    correlation_id varchar(255) not null,
    causation_id varchar(255) not null,
    producer varchar(100) not null,
    schema_version varchar(20) not null,
    transaction_id varchar(255) not null,
    reservation_request_id varchar(255) not null,
    occurred_at timestamp with time zone not null,
    consumed_at timestamp with time zone not null,
    constraint pk_ledger_posting_command_inbox primary key (event_id),
    constraint ck_ledger_posting_command_inbox_event_type
        check (event_type = 'LedgerPostingRequested.v1'),
    constraint ck_ledger_posting_command_inbox_topic
        check (topic = 'ledger.posting.requested.v1'),
    constraint ck_ledger_posting_command_inbox_producer
        check (producer = 'transaction-service'),
    constraint ck_ledger_posting_command_inbox_schema_version
        check (schema_version = '1.0.0')
);

create index idx_ledger_posting_command_inbox_posting_request
    on ledger_posting_command_inbox (posting_request_id);

create function prevent_ledger_posting_command_inbox_mutation()
returns trigger
language plpgsql
as $$
begin
    raise exception 'ledger posting command inbox rows are append-only';
end;
$$;

create trigger trg_prevent_ledger_posting_command_inbox_update
before update on ledger_posting_command_inbox
for each row execute function prevent_ledger_posting_command_inbox_mutation();

create trigger trg_prevent_ledger_posting_command_inbox_delete
before delete on ledger_posting_command_inbox
for each row execute function prevent_ledger_posting_command_inbox_mutation();
