/**
 * 
 */
package graphene.enron.dao.impl;

import graphene.dao.TransactionDAO;
import graphene.dao.sql.GenericDAOJDBCImpl;
import graphene.enron.model.sql.enron.EnronTransactionPair100;
import graphene.enron.model.sql.enron.QEnronTransactionPair100;
import graphene.model.idl.G_Link;
import graphene.model.query.EventQuery;
import graphene.util.CallBack;
import graphene.util.validator.ValidationUtils;

import java.sql.Connection;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.math.NumberUtils;
import org.apache.tapestry5.ioc.annotations.Inject;
import org.slf4j.Logger;

import com.mysema.query.BooleanBuilder;
import com.mysema.query.Tuple;
import com.mysema.query.sql.SQLQuery;

/**
 * @author djue
 * 
 */
public class TransactionDAOSQLImpl extends
		GenericDAOJDBCImpl<EnronTransactionPair100, EventQuery> implements
		TransactionDAO<EnronTransactionPair100, EventQuery> {

	@Inject
	private Logger logger;

	/**
	 * 
	 */
	public TransactionDAOSQLImpl() {
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param session
	 * @param q
	 * @param ignoreLimits
	 * @return
	 * @throws Exception
	 */
	private SQLQuery buildQuery(EventQuery q, QEnronTransactionPair100 t,
			Connection conn) throws Exception {
		BooleanBuilder builder = new BooleanBuilder();

		if (ValidationUtils.isValid(q.getIdList())) {
			List<Long> accountIntegerList = new ArrayList<Long>();
			for (String acno : q.getIdList()) {
				if (NumberUtils.isDigits(acno)) {
					accountIntegerList.add(Long.parseLong(acno));
				} else {
					logger.warn("Non numeric id provided.");
				}
			}
			if (accountIntegerList.size() > 0) {
				if (q.isIntersectionOnly()) {
					// events where both sides are in the list. Basically an
					// inner
					// join
					builder.and(t.receiverId.in(accountIntegerList).and(
							t.senderId.in(accountIntegerList)));
				} else {
					// events where either side is in the list. Basically an
					// outer
					// join
					builder.and(t.receiverId.in(accountIntegerList).or(
							t.senderId.in(accountIntegerList)));
				}
			} else if (q.getIdList().size() > 0) {
				// XXX: This is a hack. For some reason Enron is sending the
				// email as the account number (instead of an id number)--djue
				if (q.isIntersectionOnly()) {
					// events where both sides are in the list. Basically an
					// inner
					// join
					builder.and(t.receiverValueStr.in(q.getIdList()).and(
							t.senderValueStr.in(q.getIdList())));
				} else {
					// events where either side is in the list. Basically an
					// outer
					// join
					builder.and(t.receiverValueStr.in(q.getIdList()).or(
							t.senderValueStr.in(q.getIdList())));
				}
			}
		}
		if (ValidationUtils.isValid(q.getComments())) {
			builder.and(t.trnValueStr.like("%" + q.getComments() + "%"));
		}
		long start = q.getMinSecs();
		long end = q.getMaxSecs();

		if (start != 0 || end != 0) {

			if (start != end) {
				builder.and(t.trnDt.between(new Timestamp(start),
						new Timestamp(end)));
			} else if (start == end) {
				builder.and(t.trnDt.eq(new Timestamp(start)));
			} else if (start != 0) {
				builder.and(t.trnDt.goe(new Timestamp(start)));
			} else if (end != 0) {
				builder.and(t.trnDt.loe(new Timestamp(end)));
			}
		}

		double minam = q.getMinAmount();
		double maxam = q.getMaxAmount();

		if (minam != 0 || maxam != 0) {
			if (minam != maxam && maxam > 0) {
				builder.and(t.trnValueNbr.between(minam, maxam));
			} else if (minam == maxam && minam != 0 && maxam != 0) {
				builder.and(t.trnValueNbr.eq(minam));
			} else if (minam != 0) {
				builder.and(t.trnValueNbr.goe(minam));
			} else if (maxam != 0) {
				builder.and(t.trnValueNbr.loe(maxam));
			}
		}
		// OLD SQLQuery sq = from(conn, t).where(builder);
		// MFM allow sorting by user-specified column name
		SQLQuery sq = null;
		String sortCol = q.getSortColumn();
		boolean sortASC = q.isSortAscending();

		// if no sort col is specified do the next line,
		// else add the sort col and direction
		if (sortCol == null || sortCol.length() == 0) {
			sq = from(conn, t).where(builder).orderBy(t.trnDt.asc());
		} else {
			// Some or all of the column names in the GUI data model don't match
			// the
			// corresponding DB column names, so we have to map the column names
			// from the gui for sorting.
			// MFM: This is tightly coupled with the GUI and is bad practice.
			// Need to Fix later.
			if (sortCol.equals("trn_dt") || sortCol.equals("date")) {
				if (sortASC) {
					sq = from(conn, t).where(builder).orderBy(t.trnDt.asc());
				} else {
					sq = from(conn, t).where(builder).orderBy(t.trnDt.desc());
				}
			} else {
				if (sortCol.equals("senderId")) {
					if (sortASC) {
						sq = from(conn, t).where(builder).orderBy(
								t.senderId.asc());
					} else {
						sq = from(conn, t).where(builder).orderBy(
								t.senderId.desc());
					}
				} else if (sortCol.equals("receiverId")) {
					if (sortASC) {
						sq = from(conn, t).where(builder).orderBy(
								t.receiverId.asc());
					} else {
						sq = from(conn, t).where(builder).orderBy(
								t.receiverId.desc());
					}
				} else if (sortCol.equals("comments")) {
					if (sortASC) {
						sq = from(conn, t).where(builder).orderBy(
								t.trnValueStr.asc());
					} else {
						sq = from(conn, t).where(builder).orderBy(
								t.trnValueStr.desc());
					}

				} else if (sortCol.equals("unit")) {
					if (sortASC) {
						sq = from(conn, t).where(builder).orderBy(
								t.trnValueNbrUnit.asc());
					} else {
						sq = from(conn, t).where(builder).orderBy(
								t.trnValueNbrUnit.desc());
					}
				}

				else if (sortCol.equals("amount")) {
					// temporary
					if (sortASC) {
						sq = from(conn, t).where(builder).orderBy(
								t.trnValueNbrUnit.asc(), t.trnValueNbr.asc());
					} else {
						sq = from(conn, t).where(builder).orderBy(
								t.trnValueNbrUnit.asc(), t.trnValueNbr.desc());
					}
				}

			}
		}

		return sq;
	}

	/**
	 * Similar to findByQuery, but just a count.
	 */
	@Override
	public long count(EventQuery q) throws Exception {
		long results = 0;
		QEnronTransactionPair100 t = new QEnronTransactionPair100("t");
		Connection conn;
		conn = getConnection();
		SQLQuery sq = buildQuery(q, t, conn).orderBy(t.trnDt.asc());
		results = sq.count();
		conn.close();

		logger.debug("Counted " + results + " entries");

		return results;
	}

	/**
	 * TODO: This is the same as getTransactions, so migrate usage to this
	 * method.
	 */
	@Override
	public List<EnronTransactionPair100> findByQuery(long offset,
			long maxResults, EventQuery q) throws Exception {
		List<EnronTransactionPair100> results;
		QEnronTransactionPair100 t = new QEnronTransactionPair100("t");
		Connection conn;
		conn = getConnection();
		// OLD SQLQuery sq = buildQuery(q, t, conn).orderBy(t.trnDt.asc());
		SQLQuery sq = buildQuery(q, t, conn); // MFM
		sq = setOffsetAndLimit(q, sq);
		results = sq.list(t);
		conn.close();
		if (results != null) {
			logger.debug("Returning " + results.size() + " entries");
		}
		return results;
	}

	/**
	 * @param q
	 * @return
	 * @throws Exception
	 */
	public ArrayList<G_Link> getAccounts(EventQuery q) throws Exception {
		ArrayList<G_Link> results = new ArrayList<G_Link>();
		QEnronTransactionPair100 t = new QEnronTransactionPair100("t");
		BooleanBuilder builder = new BooleanBuilder();
		for (String acno : q.getIdList()) {
			builder.or(t.receiverId.eq(Long.parseLong(acno)));
			builder.or(t.senderId.eq(Long.parseLong(acno)));
		}
		Connection conn = getConnection();
		SQLQuery sq = from(conn, t).where(builder).orderBy(t.receiverId.asc());
		sq = setOffsetAndLimit(q, sq);
		List<Tuple> list = sq.list(t.receiverId, t.senderId);
		for (Tuple tuple : list) {
			// TODO: fill in more fields
			G_Link link = new G_Link(tuple.get(0, String.class), tuple.get(1,
					String.class), true, null, null, null, null);

			results.add(link);
		}
		logger.debug("Returning " + results.size() + " entries");

		return results;
	}

	/**
	 * Essentially the same as getTransactions, but only looks at offset and max
	 * results as parameters
	 */
	@Override
	public List<EnronTransactionPair100> getAll(long offset, long maxResults)
			throws Exception {
		List<EnronTransactionPair100> results;
		QEnronTransactionPair100 t = new QEnronTransactionPair100("t");
		Connection conn;
		conn = getConnection();
		SQLQuery sq = from(conn, t).offset(offset).limit(maxResults);
		results = sq.list(t);
		conn.close();
		if (results != null) {
			logger.debug("Returning " + results.size() + " entries");
		}
		return results;
	}

	@Override
	public boolean performCallback(long offset, long maxResults,
			CallBack<EnronTransactionPair100> cb, EventQuery q) {
		return basicCallback(offset, maxResults, cb, q);
	}

	/**
	 * Equivalent to
	 * 
	 * String query =
	 * "Select count(*) From (Select distinct Acct_Nbr_Sender, Acct_Nbr_Receiver "
	 * + " from pairsTable " +
	 * " where Acct_Nbr_Sender = ? or Acct_Nbr_Receiver = ?) as ct";
	 * 
	 * @throws Exception
	 */
	@Override
	public long countEdges(String id) throws Exception {
		long result = 0;
		QEnronTransactionPair100 t = new QEnronTransactionPair100("t");
		Connection conn = getConnection();
		Long idNumber = Long.parseLong(id);
		result = from(conn, t)
				.where(t.senderId.eq(idNumber).or(t.receiverId.eq(idNumber)))
				.distinct().count();
		conn.close();
		return result;
	}

	@Override
	public boolean performThrottlingCallback(long offset, long maxResults,
			CallBack<EnronTransactionPair100> cb, EventQuery q) {
		return throttlingCallback(offset, maxResults, cb, q);
	}

}