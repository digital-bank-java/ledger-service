create function prevent_ledger_entry_mutation()
returns trigger
language plpgsql
as $$
begin
    raise exception 'ledger entries are append-only and cannot be modified';
end;
$$;

create trigger trg_prevent_ledger_entries_update
before update on ledger_entries
for each row execute function prevent_ledger_entry_mutation();

create trigger trg_prevent_ledger_entries_delete
before delete on ledger_entries
for each row execute function prevent_ledger_entry_mutation();

create trigger trg_prevent_ledger_entry_lines_update
before update on ledger_entry_lines
for each row execute function prevent_ledger_entry_mutation();

create trigger trg_prevent_ledger_entry_lines_delete
before delete on ledger_entry_lines
for each row execute function prevent_ledger_entry_mutation();
