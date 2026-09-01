create table ledger_outbox_events (
    event_id uuid not null,
    event_type varchar(100) not null,
    aggregate_id varchar(100) not null,
    posting_request_id varchar(100),
    correlation_id varchar(255) not null,
    causation_id varchar(255) not null,
    payload jsonb not null,
    status varchar(20) not null default 'PENDING',
    attempts integer not null default 0,
    created_at timestamp with time zone not null default now(),
    published_at timestamp with time zone,
    constraint pk_ledger_outbox_events primary key (event_id),
    constraint ck_ledger_outbox_events_status check (status in ('PENDING', 'PUBLISHED')),
    constraint ck_ledger_outbox_events_attempts check (attempts >= 0)
);

create unique index uq_ledger_outbox_completed_aggregate
    on ledger_outbox_events (aggregate_id)
    where event_type = 'LedgerPostingCompleted.v1';

create index idx_ledger_outbox_pending
    on ledger_outbox_events (status, created_at)
    where status = 'PENDING';

create index idx_ledger_outbox_aggregate_id on ledger_outbox_events (aggregate_id);

create function prevent_ledger_outbox_mutation()
returns trigger
language plpgsql
as $$
begin
    raise exception 'ledger outbox events are append-only and cannot be modified';
end;
$$;

create trigger trg_prevent_ledger_outbox_events_update
before update on ledger_outbox_events
for each row execute function prevent_ledger_outbox_mutation();

create trigger trg_prevent_ledger_outbox_events_delete
before delete on ledger_outbox_events
for each row execute function prevent_ledger_outbox_mutation();
