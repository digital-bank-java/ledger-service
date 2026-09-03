alter table ledger_outbox_events
    add column next_attempt_at timestamp with time zone not null default now(),
    add column lease_id uuid,
    add column lease_expires_at timestamp with time zone,
    add column last_error varchar(1000),
    add column quarantined_at timestamp with time zone;

alter table ledger_outbox_events
    drop constraint ck_ledger_outbox_events_status,
    add constraint ck_ledger_outbox_events_status
        check (status in ('PENDING', 'DELIVERING', 'PUBLISHED', 'QUARANTINED'));

drop index idx_ledger_outbox_pending;
create index idx_ledger_outbox_deliverable
    on ledger_outbox_events (status, next_attempt_at, created_at)
    where status = 'PENDING';

create index idx_ledger_outbox_expired_leases
    on ledger_outbox_events (lease_expires_at, created_at)
    where status = 'DELIVERING';

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

    if new.attempts < old.attempts then
        raise exception 'ledger outbox attempts cannot decrease';
    end if;

    if new.status = 'PENDING' then
        if new.published_at is not null or new.lease_id is not null or new.lease_expires_at is not null
            or new.next_attempt_at is null then
            raise exception 'invalid pending ledger outbox delivery state';
        end if;
    elsif new.status = 'DELIVERING' then
        if new.published_at is not null or new.quarantined_at is not null or new.lease_id is null
            or new.lease_expires_at is null or new.attempts <= old.attempts then
            raise exception 'invalid delivering ledger outbox delivery state';
        end if;
    elsif new.status = 'PUBLISHED' then
        if new.published_at is null or new.lease_id is not null or new.lease_expires_at is not null then
            raise exception 'invalid published ledger outbox delivery state';
        end if;
    elsif new.status = 'QUARANTINED' then
        if new.quarantined_at is null or new.last_error is null or new.lease_id is not null
            or new.lease_expires_at is not null then
            raise exception 'invalid quarantined ledger outbox delivery state';
        end if;
    else
        raise exception 'invalid ledger outbox delivery status';
    end if;

    return new;
end;
$$;
