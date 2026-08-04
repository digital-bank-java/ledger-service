alter table ledger_entries
    add column request_fingerprint varchar(64),
    add column reversal_of_entry_id uuid;

alter table ledger_entries
    add constraint fk_ledger_entries_reversal_source
        foreign key (reversal_of_entry_id) references ledger_entries (id),
    add constraint uq_ledger_entries_reversal_source unique (reversal_of_entry_id);

create index idx_ledger_entries_request_fingerprint on ledger_entries (request_fingerprint);
