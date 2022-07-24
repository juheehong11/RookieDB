    void restartAnalysis() {
        // Read master record
        LogRecord record = logManager.fetchLogRecord(0L);
        // Type checking
        assert (record != null && record.getType() == LogType.MASTER);
        MasterLogRecord masterRecord = (MasterLogRecord) record;
        // Get start checkpoint LSN
        long LSN = masterRecord.lastCheckpointLSN;
        // Set of transactions that have completed
        Set<Long> endedTransactions = new HashSet<>();
        // TODO(proj5): implement

        Iterator<LogRecord> iterRec = logManager.scanFrom(LSN);
        while (iterRec.hasNext()) {
            LogRecord logrec = iterRec.next();
            long logrecLSN = logrec.getLSN();
            if (logRecord.getTransNum().isPresent()) {
                long tn = logrec.getTransNum().get();
                if (!transactionTable.containsKey(tn)) {
                    Transaction txn = newTransaction.apply(tn);
                    startTransaction(txn);
                    TransactionTableEntry newEntry = new TransactionTableEntry(txn);
                    newEntry.lastLSN = logrecLSN;
                    transactionTable.put(tn, newEntry);
                }
            }

            LogType logrecType = logrec.getType();
            if (logrec.getPageNum().isPresent()) {
                long pgnum = logrec.getPageNum().get();
                if (logrecType == LogType.UPDATE_PAGE || logrecType == LogType.UNDO_UPDATE_PAGE) {
                    if (!dirtyPageTable.containsKey(pgnu)) {
                        dirtyPageTable.put(pgnum, logrecLSN);
                    }
                } else if (logrecType == LogType.FREE_PAGE || logrecType == LogType.UNDO_ALLOC_PAGE) {
                    dirtyPageTable.remove(pgnum);
                }
            }

            long tn = logrec.getTransNum().get();
            TransactionTableEntry entry = transactionTable.get(tn);

            if (logrecType == LogType.COMMIT_TRANSACTION) {
                entry.transaction.setStatus(Transaction.Status.COMMITTING);
            } else if (logrecType == LogType.ABORT_TRANSACTION) {
                entry.transaction.setStatus(Transaction.Status.RECOVERY_ABORTING);
            } else if (logrecType == LogType.END_TRANSACTION) {
                entry.transaction.cleanup();
                entry.transaction.setStatus(Transaction.Status.COMPLETE);
                transactionTable.remove(tn);
                endedTransactions.add(tn);
            }

            if (logrecType == LogType.END_CHECKPOINT) {
                Map<Long, Long> checkpointDPT = logrec.getDirtyPageTable();
                Map<Long, Pair<Transaction.Status, Long>> checkpointTxnTable = logrec.getTransactionTable();

                for (Map.Entry<Long, Long> entry : checkpointDPT.entrySet()) {
                    dirtyPageTable.put(entry.getKey(), entry.getValue());
                }

                for (Map.Entry<Long, Pair<Transaction.Status, Long>> txn : checkpointTxnTable.entrySet()) {
                    long tn = txn.getKey();
                    Pair<Transaction.Status, Long> pair = txn.getValue();
                    Transaction.Status stat = pair.getFirst();
                    long checkpointLLSN = pair.getSecond();
            
                    if (endedTransactions.contains(txn.getKey())) {
                        continue;
                    }

                    if (!transactionTable.containsKey(tn)) {
                        Transaction txn = newTransaction.apply(tn);
                        startTransaction(txn);
                        TransactionTableEntry newEntry = new TransactionTableEntry(txn);
                        if (newEntry.lastLSN < checkpointLLSN) {
                            newEntry.lastLSN = checkpointLLSN;
                        }
                        transactionTable.put(tn, newEntry);
                    }

                    TransactionTableEntry entry = transactionTable.get(tn);

                    if (stat == Transaction.Status.ABORTING &&
                            entry.transaction.getStatus() == Transaction.Status.RUNNING) {
                        entry.transaction.setStatus(Transaction.Status.RECOVERY_ABORTING);
                    } else if () {

                    }
                }
            }
        }
        // Ending Transactions
        for (TransactionTableEntry entry : transactionTable.values()) {
            long tn = entry.transaction.getTransNum();
            Transaction.Status entryStatus = entry.transaction.getStatus()
            if (entryStatus == Transaction.Status.COMMITTING) {
                entry.transaction.cleanup();
                entry.transaction.setStatus(Transaction.Status.COMPLETE);
                EndTransactionLogRecord newlog = new EndTransactionLogRecord(tn, entry.lastLSN);
                transactionTable.remove(tn);
                logManager.appendToLog(newlog);
            } else if (entryStatus == Transaction.Status.RUNNING) {
                entry.transaction.setStatus(Transaction.Status.RECOVERY_ABORTING);
                AbortTransactionLogRecord newlog = new AbortTransactionLogRecord(entry.transaction.getTransNum(), entry.lastLSN);
                logManager.appendToLog(newlog); 
            }
        }
    }