package nxt;

import nxt.db.*;
import nxt.util.Convert;

import java.sql.*;
import java.util.Arrays;
import java.util.List;

public class PendingTransactionPoll extends AbstractPoll {
    private final long id;
    private final DbKey dbKey;
    private final long[] whitelist;
    private final long[] blacklist;
    private final long quorum;

    private static final DbKey.LongKeyFactory<PendingTransactionPoll> pollDbKeyFactory = new DbKey.LongKeyFactory<PendingTransactionPoll>("id") {
        @Override
        public DbKey newKey(PendingTransactionPoll poll) {
            return poll.dbKey;
        }
    };

    private static final DbKey.LongKeyFactory<PendingTransactionPoll> signersDbKeyFactory = new DbKey.LongKeyFactory<PendingTransactionPoll>("poll_id") {
        @Override
        public DbKey newKey(PendingTransactionPoll poll) {
            return poll.dbKey;
        }
    };

    final static VersionedValuesDbTable<PendingTransactionPoll, Long> signersTable = new VersionedValuesDbTable<PendingTransactionPoll, Long>("pending_transaction_signer", signersDbKeyFactory) {

        @Override
        protected Long load(Connection con, ResultSet rs) throws SQLException {
            return rs.getLong("account_id");
        }

        @Override
        protected void save(Connection con, PendingTransactionPoll poll, Long accountId) throws SQLException {
            try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO " + table + "(poll_id, "
                    + "account_id, height) VALUES (?, ?, ?)")) {
                int i = 0;
                pstmt.setLong(++i, poll.getId());
                pstmt.setLong(++i, accountId);
                pstmt.setInt(++i, Nxt.getBlockchain().getHeight());
                pstmt.executeUpdate();
            }
        }
    };


    private final static VersionedEntityDbTable<PendingTransactionPoll> pendingTransactionsTable =
            new VersionedEntityDbTable<PendingTransactionPoll>("pending_transaction", pollDbKeyFactory) {

        @Override
        protected PendingTransactionPoll load(Connection con, ResultSet rs) throws SQLException {
            return new PendingTransactionPoll(rs);
        }

        @Override
        protected void save(Connection con, PendingTransactionPoll poll) throws SQLException {
            poll.save(con);
        }
    };

    static void init() {
    }

    PendingTransactionPoll(long id, long accountId, int finishBlockHeight,
                                  byte votingModel, long quorum, long voteThreshold,
                                  long assetId, long[] whitelist, long[] blacklist) {
        super(accountId, finishBlockHeight, votingModel, assetId, voteThreshold);
        this.id = id;
        this.dbKey = pollDbKeyFactory.newKey(this.id);
        this.quorum = quorum;

        if (whitelist != null) {
            this.whitelist = whitelist;
            Arrays.sort(this.whitelist);
        } else {
            this.whitelist = new long[0];
        }


        if (blacklist != null) {
            this.blacklist = blacklist;
            Arrays.sort(this.blacklist);
        } else {
            this.blacklist = new long[0];
        }
    }

    private PendingTransactionPoll(ResultSet rs) throws SQLException {
        super(rs);
        this.id = rs.getLong("id");
        this.quorum = rs.getLong("quorum");
        this.dbKey = pollDbKeyFactory.newKey(this.id);

        byte signersCount = rs.getByte("signers_count");
        if (signersCount == 0) {
            this.whitelist = new long[0];
            this.blacklist = new long[0];
        } else {
            List<Long> signers = signersTable.get(signersDbKeyFactory.newKey(this));
            boolean isBlacklist = rs.getBoolean("blacklist");
            if (isBlacklist) {
                this.whitelist = new long[0];
                this.blacklist = Convert.reversedListOfLongsToArray(signers);
            } else {
                this.whitelist = Convert.reversedListOfLongsToArray(signers);
                this.blacklist = new long[0];
            }
        }
    }

    public long getId() {
        return id;
    }

    public static void addPoll(PendingTransactionPoll poll){
        pendingTransactionsTable.insert(poll);

        long[] signers;

        if (poll.getBlacklist().length > 0) {
            signers = poll.getBlacklist();
        } else {
            signers = poll.getWhitelist();
        }

        if (signers.length > 0) {
            signersTable.insert(poll, Convert.arrayOfLongsToList(signers));
        }
    }

    public static DbIterator<PendingTransactionPoll> finishing(int height){
        return pendingTransactionsTable.getManyBy(new DbClause.IntClause("finish_height", height), 0, Integer.MAX_VALUE);
    }

    public static PendingTransactionPoll getPoll(long id) {
        return pendingTransactionsTable.getBy(new DbClause.LongClause("id", id));
    }

    public static DbIterator<PendingTransactionPoll> getByAccountId(long accountId, int firstIndex, int lastIndex) {
        DbClause clause = new DbClause.LongClause("account_id", accountId);
        return pendingTransactionsTable.getManyBy(clause, firstIndex, lastIndex);
    }

    public static DbIterator<PendingTransactionPoll> getByAssetId(long assetId, int firstIndex, int lastIndex) {
        DbClause clause = new DbClause.LongClause("holding_id", assetId);
        return pendingTransactionsTable.getManyBy(clause, firstIndex, lastIndex);
    }

    public static List<Long> getIdsByWhitelistedSigner(Account signer,  int from, int to) {
        try (Connection con = Db.db.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT DISTINCT pending_transaction.id "
                     + "from pending_transaction, pending_transactions_signers "
                     + "WHERE pending_transaction.latest = TRUE AND pending_transaction.finished = false AND "
                     + "pending_transaction.blacklist = false AND "
                     + "pending_transaction.id = pending_transactions_signers.poll_id "
                     + "AND pending_transaction_signers.account_id = ? "
                     + DbUtils.limitsClause(from, to))) {
            pstmt.setLong(1, signer.getId());
            DbUtils.setLimits(2, pstmt, from, to);

            DbIterator<Long> iterator = new DbIterator<>(con, pstmt, new DbIterator.ResultSetReader<Long>() {
                @Override
                public Long get(Connection con, ResultSet rs) throws Exception {
                    return rs.getLong(1);
                }
            });

            return iterator.toList();
        } catch (SQLException e) {
            throw new NxtException.StopException(e.toString(), e);
        }
    }


    public long[] getWhitelist() {
        return whitelist;
    }

    public long[] getBlacklist() {
        return blacklist;
    }

    public long getQuorum() { return quorum; }

    private void save(Connection con) throws SQLException {
        boolean isBlacklist;
        byte signersCount;

        if (getBlacklist().length > 0) {
            isBlacklist = true;
            signersCount = (byte)getBlacklist().length;
        } else {
            isBlacklist = false;
            signersCount = (byte)getWhitelist().length;
        }

        try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO pending_transaction (id, account_id, "
                + "finish_height, signers_count, blacklist, voting_model, quorum, min_balance, holding_id, "
                + "finished, height) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            int i = 0;
            pstmt.setLong(++i, getId());
            pstmt.setLong(++i, getAccountId());
            pstmt.setInt(++i, getFinishBlockHeight());
            pstmt.setByte(++i, signersCount);
            pstmt.setBoolean(++i, isBlacklist);
            pstmt.setByte(++i, getVotingModel());
            pstmt.setLong(++i, getQuorum());
            pstmt.setLong(++i, getMinBalance());
            pstmt.setLong(++i, getHoldingId());
            pstmt.setBoolean(++i, isFinished());
            pstmt.setInt(++i, Nxt.getBlockchain().getHeight());
            pstmt.executeUpdate();
        }
    }

    static void finishPoll(PendingTransactionPoll poll) {
        pendingTransactionsTable.delete(poll);
        signersTable.delete(poll);
        //todo: clear VOTE_PHASED table as well
    }
}
