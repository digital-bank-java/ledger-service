alter table ledger_outbox_events
    add column transaction_id varchar(255),
    add column reservation_request_id varchar(255),
    add column decision_id uuid;

create unique index uq_ledger_outbox_failed_posting_request
    on ledger_outbox_events (posting_request_id)
    where event_type = 'LedgerPostingFailed.v1';

create table ledger_posting_failure_decisions (
    decision_id uuid not null,
    event_id uuid not null,
    posting_request_id varchar(100) not null,
    failure_code varchar(50) not null,
    failure_reason varchar(500) not null,
    correlation_id varchar(255) not null,
    causation_id varchar(255) not null,
    transaction_id varchar(255),
    reservation_request_id varchar(255),
    occurred_at timestamp with time zone not null,
    constraint pk_ledger_posting_failure_decisions primary key (decision_id),
    constraint uq_ledger_posting_failure_decisions_event unique (event_id),
    constraint uq_ledger_posting_failure_decisions_request unique (posting_request_id),
    constraint ck_ledger_posting_failure_decisions_code
        check (failure_code in ('VALIDATION_ERROR', 'CONFLICT', 'ACCOUNTING_ERROR', 'INTERNAL_ERROR'))
);

create function prevent_ledger_posting_failure_decision_mutation()
returns trigger
language plpgsql
as $$
begin
    raise exception 'ledger posting failure decisions are append-only';
end;
$$;

create trigger trg_prevent_ledger_posting_failure_decisions_update
before update on ledger_posting_failure_decisions
for each row execute function prevent_ledger_posting_failure_decision_mutation();

create trigger trg_prevent_ledger_posting_failure_decisions_delete
before delete on ledger_posting_failure_decisions
for each row execute function prevent_ledger_posting_failure_decision_mutation();

create or replace function prevent_ledger_outbox_mutation()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE' then
        raise exception 'ledger outbox events are append-only and cannot be deleted';
    end if;

    if new.event_id is distinct from old.event_id
        or new.event_type is distinct from old.event_type
        or new.aggregate_id is distinct from old.aggregate_id
        or new.posting_request_id is distinct from old.posting_request_id
        or new.correlation_id is distinct from old.correlation_id
        or new.causation_id is distinct from old.causation_id
        or new.transaction_id is distinct from old.transaction_id
        or new.reservation_request_id is distinct from old.reservation_request_id
        or new.decision_id is distinct from old.decision_id
        or new.payload is distinct from old.payload
        or new.created_at is distinct from old.created_at then
        raise exception 'ledger outbox event identity and payload fields are immutable';
    end if;

    if old.status = 'PUBLISHED' then
        raise exception 'published ledger outbox events are immutable';
    end if;

    if new.status = 'PENDING' then
        if new.published_at is not null or new.attempts < old.attempts then
            raise exception 'invalid pending ledger outbox delivery state';
        end if;
    elsif new.status = 'PUBLISHED' then
        if new.published_at is null or new.attempts < old.attempts then
            raise exception 'invalid published ledger outbox delivery state';
        end if;
    else
        raise exception 'invalid ledger outbox delivery status';
    end if;

    return new;
end;
$$;
