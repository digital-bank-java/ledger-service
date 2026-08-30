create or replace function prevent_ledger_outbox_mutation()
returns trigger
language plpgsql
as $$
begin
    if new.event_id is distinct from old.event_id
        or new.event_type is distinct from old.event_type
        or new.aggregate_id is distinct from old.aggregate_id
        or new.posting_request_id is distinct from old.posting_request_id
        or new.correlation_id is distinct from old.correlation_id
        or new.causation_id is distinct from old.causation_id
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
