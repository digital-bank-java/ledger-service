update ledger_outbox_events
set status = 'QUARANTINED',
    quarantined_at = coalesce(quarantined_at, now()),
    last_error = 'Legacy outbox event is missing governed posting metadata',
    lease_id = null,
    lease_expires_at = null
where status in ('PENDING', 'DELIVERING')
  and (nullif(btrim(posting_request_id), '') is null
    or nullif(btrim(correlation_id), '') is null
    or nullif(btrim(causation_id), '') is null
    or nullif(btrim(transaction_id), '') is null
    or nullif(btrim(reservation_request_id), '') is null);

alter table ledger_outbox_events
    add constraint ck_ledger_outbox_governed_metadata
    check (
        status not in ('PENDING', 'DELIVERING')
        or (nullif(btrim(posting_request_id), '') is not null
            and nullif(btrim(correlation_id), '') is not null
            and nullif(btrim(causation_id), '') is not null
            and nullif(btrim(transaction_id), '') is not null
            and nullif(btrim(reservation_request_id), '') is not null)
    );

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

    if new.attempts < old.attempts
        and not (old.status = 'QUARANTINED' and new.status = 'PENDING' and new.attempts = 0) then
        raise exception 'ledger outbox attempts cannot decrease';
    end if;

    if new.status = 'PENDING' then
        if old.status not in ('PENDING', 'DELIVERING', 'QUARANTINED')
            or new.published_at is not null or new.lease_id is not null or new.lease_expires_at is not null
            or new.next_attempt_at is null or new.quarantined_at is not null
            or nullif(btrim(new.posting_request_id), '') is null
            or nullif(btrim(new.correlation_id), '') is null
            or nullif(btrim(new.causation_id), '') is null
            or nullif(btrim(new.transaction_id), '') is null
            or nullif(btrim(new.reservation_request_id), '') is null then
            raise exception 'invalid pending ledger outbox delivery state';
        end if;
    elsif new.status = 'DELIVERING' then
        if old.status not in ('PENDING', 'DELIVERING')
            or new.published_at is not null or new.quarantined_at is not null or new.lease_id is null
            or new.lease_expires_at is null or new.attempts <= old.attempts
            or nullif(btrim(new.posting_request_id), '') is null
            or nullif(btrim(new.correlation_id), '') is null
            or nullif(btrim(new.causation_id), '') is null
            or nullif(btrim(new.transaction_id), '') is null
            or nullif(btrim(new.reservation_request_id), '') is null then
            raise exception 'invalid delivering ledger outbox delivery state';
        end if;
    elsif new.status = 'PUBLISHED' then
        if old.status <> 'DELIVERING'
            or new.published_at is null or new.lease_id is not null or new.lease_expires_at is not null then
            raise exception 'invalid published ledger outbox delivery state';
        end if;
    elsif new.status = 'QUARANTINED' then
        if old.status <> 'DELIVERING'
            or new.quarantined_at is null or new.last_error is null or new.lease_id is not null
            or new.lease_expires_at is not null then
            raise exception 'invalid quarantined ledger outbox delivery state';
        end if;
    else
        raise exception 'invalid ledger outbox delivery status';
    end if;

    return new;
end;
$$;
