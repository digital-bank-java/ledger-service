create table ledger_entries (
    id uuid not null,
    posting_request_id varchar(100) not null,
    description varchar(255) not null,
    currency varchar(3) not null,
    effective_at timestamp with time zone not null,
    created_at timestamp with time zone not null default now(),
    constraint pk_ledger_entries primary key (id),
    constraint uq_ledger_entries_posting_request_id unique (posting_request_id),
    constraint ck_ledger_entries_currency_format check (currency = upper(currency))
);

create table ledger_entry_lines (
    id bigint generated always as identity,
    entry_id uuid not null,
    line_number integer not null,
    account_id uuid not null,
    line_type varchar(10) not null,
    amount numeric(19, 4) not null,
    constraint pk_ledger_entry_lines primary key (id),
    constraint fk_ledger_entry_lines_entry
        foreign key (entry_id) references ledger_entries (id) on delete cascade,
    constraint uq_ledger_entry_lines_entry_line_number unique (entry_id, line_number),
    constraint ck_ledger_entry_lines_type check (line_type in ('DEBIT', 'CREDIT')),
    constraint ck_ledger_entry_lines_amount_positive check (amount > 0)
);

create index idx_ledger_entry_lines_account_id on ledger_entry_lines (account_id);
