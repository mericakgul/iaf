/*
   Copyright 2021 WeAreFrank!

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package nl.nn.adapterframework.scheduler.job;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import lombok.Getter;
import nl.nn.adapterframework.configuration.Configuration;
import nl.nn.adapterframework.configuration.IbisManager;
import nl.nn.adapterframework.core.IAdapter;
import nl.nn.adapterframework.core.IExtendedPipe;
import nl.nn.adapterframework.core.IPipe;
import nl.nn.adapterframework.core.ITransactionalStorage;
import nl.nn.adapterframework.core.PipeLine;
import nl.nn.adapterframework.doc.IbisDoc;
import nl.nn.adapterframework.jdbc.FixedQuerySender;
import nl.nn.adapterframework.jdbc.JdbcTransactionalStorage;
import nl.nn.adapterframework.parameters.Parameter;
import nl.nn.adapterframework.pipes.MessageSendingPipe;
import nl.nn.adapterframework.receivers.Receiver;
import nl.nn.adapterframework.scheduler.JobDef;
import nl.nn.adapterframework.stream.Message;
import nl.nn.adapterframework.util.AppConstants;
import nl.nn.adapterframework.util.DateUtils;
import nl.nn.adapterframework.util.MessageKeeper.MessageKeeperLevel;
import nl.nn.adapterframework.util.SpringUtils;

public class CleanupDatabaseJob extends JobDef {
	private @Getter int queryTimeout;

	private class MessageLogObject {
		private String datasourceName;
		private String tableName;
		private String expiryDateField;
		private String keyField;
		private String typeField;

		public MessageLogObject(String datasourceName, String tableName, String expiryDateField, String keyField, String typeField) {
			this.datasourceName = datasourceName;
			this.tableName = tableName;
			this.expiryDateField = expiryDateField;
			this.keyField = keyField;
			this.typeField = typeField;
		}

		@Override
		public boolean equals(Object o) {
			if(o == null || !(o instanceof MessageLogObject)) return false;

			MessageLogObject mlo = (MessageLogObject) o;
			if (mlo.getDatasourceName().equals(datasourceName) &&
				mlo.getTableName().equals(tableName) &&
				mlo.expiryDateField.equals(expiryDateField)) {
				return true;
			} else {
				return false;
			}
		}

		public String getDatasourceName() {
			return datasourceName;
		}

		public String getTableName() {
			return tableName;
		}

		public String getExpiryDateField() {
			return expiryDateField;
		}

		public String getKeyField() {
			return keyField;
		}

		public String getTypeField() {
			return typeField;
		}
	}

	@Override
	public void execute(IbisManager ibisManager) {
		Date date = new Date();

		int maxRows = AppConstants.getInstance().getInt("cleanup.database.maxrows", 25000);

		List<String> datasourceNames = getAllLockerDatasourceNames(ibisManager);

		for (Iterator<String> iter = datasourceNames.iterator(); iter.hasNext();) {
			String datasourceName = iter.next();
			FixedQuerySender qs = null;
			try {
				qs = SpringUtils.createBean(getApplicationContext(), FixedQuerySender.class);
				qs.setDatasourceName(datasourceName);
				qs.setName("cleanupDatabase-IBISLOCK");
				qs.setQueryType("other");
				qs.setTimeout(getQueryTimeout());
				qs.setScalar(true);
				String query = "DELETE FROM IBISLOCK WHERE EXPIRYDATE < ?";
				qs.setQuery(query);
				Parameter param = new Parameter();
				param.setName("now");
				param.setType(Parameter.TYPE_TIMESTAMP);
				param.setValue(DateUtils.format(date));
				qs.addParameter(param);
				qs.configure();
				qs.open();

				Message result = qs.sendMessage(Message.nullMessage(), null);
				String resultString = result.asString();
				int numberOfRowsAffected = Integer.valueOf(resultString);
				if(numberOfRowsAffected > 0) {
					getMessageKeeper().add("deleted ["+numberOfRowsAffected+"] row(s) from [IBISLOCK] table. It implies that there have been process(es) that finished unexpectedly or failed to complete. Please investigate the log files!", MessageKeeperLevel.WARN);
				}
			} catch (Exception e) {
				String msg = "error while cleaning IBISLOCK table (as part of scheduled job execution): " + e.getMessage();
				getMessageKeeper().add(msg, MessageKeeperLevel.ERROR);
				log.error(getLogPrefix()+msg);
			} finally {
				if(qs != null) {
					qs.close();
				}
			}
		}

		List<MessageLogObject> messageLogs = getAllMessageLogs(ibisManager);

		for (MessageLogObject mlo: messageLogs) {
			FixedQuerySender qs = null;
			try {
				qs = SpringUtils.createBean(getApplicationContext(), FixedQuerySender.class);
				qs.setDatasourceName(mlo.getDatasourceName());
				qs.setName("cleanupDatabase-"+mlo.getTableName());
				qs.setQueryType("other");
				qs.setTimeout(getQueryTimeout());
				qs.setScalar(true);

				Parameter param = new Parameter();
				param.setName("now");
				param.setType(Parameter.TYPE_TIMESTAMP);
				param.setValue(DateUtils.format(date));
				qs.addParameter(param);

				String query = qs.getDbmsSupport().getCleanUpIbisstoreQuery(mlo.getTableName(), mlo.getKeyField(), mlo.getTypeField(), mlo.getExpiryDateField(), maxRows);
				qs.setQuery(query);
				qs.configure();
				qs.open();

				boolean deletedAllRecords = false;
				while(!deletedAllRecords) {
					Message result = qs.sendMessage(Message.nullMessage(), null);
					String resultString = result.asString();
					log.info("deleted [" + resultString + "] rows");
					int numberOfRowsAffected = Integer.valueOf(resultString);
					if(maxRows<=0 || numberOfRowsAffected<maxRows) {
						deletedAllRecords = true;
					} else {
						log.info("executing the query again for job [cleanupDatabase]!");
					}
				}
			} catch (Exception e) {
				String msg = "error while deleting expired records from table ["+mlo.getTableName()+"] (as part of scheduled job execution): " + e.getMessage();
				getMessageKeeper().add(msg, MessageKeeperLevel.ERROR);
				log.error(getLogPrefix()+msg);
			} finally {
				if(qs != null) {
					qs.close();
				}
			}
		}
	}

	/**
	 * Locate all Lockers, and find out which datasources are used.
	 * @return distinct list of all datasourceNames used by lockers
	 */
	protected List<String> getAllLockerDatasourceNames(IbisManager ibisManager) {
		List<String> datasourceNames = new ArrayList<>();

		for (Configuration configuration : ibisManager.getConfigurations()) {
			for (JobDef jobdef : configuration.getScheduledJobs()) {
				if (jobdef.getLocker()!=null) {
					String datasourceName = jobdef.getLocker().getDatasourceName();
					if(StringUtils.isNotEmpty(datasourceName) && !datasourceNames.contains(datasourceName)) {
						datasourceNames.add(datasourceName);
					}
				}
			}
		}

		for (IAdapter adapter : ibisManager.getRegisteredAdapters()) {
			PipeLine pipeLine = adapter.getPipeLine();
			if (pipeLine != null) {
				for (IPipe pipe : pipeLine.getPipes()) {
					if (pipe instanceof IExtendedPipe) {
						IExtendedPipe extendedPipe = (IExtendedPipe)pipe;
						if (extendedPipe.getLocker() != null) {
							String datasourceName = extendedPipe.getLocker().getDatasourceName();
							if(StringUtils.isNotEmpty(datasourceName) && !datasourceNames.contains(datasourceName)) {
								datasourceNames.add(datasourceName);
							}
						}
					}
				}
			}
		}

		return datasourceNames;
	}

	private void collectMessageLogs(List<MessageLogObject> messageLogs, ITransactionalStorage<?> transactionalStorage) {
		if (transactionalStorage!=null && transactionalStorage instanceof JdbcTransactionalStorage) {
			JdbcTransactionalStorage<?> messageLog = (JdbcTransactionalStorage<?>)transactionalStorage;
			String datasourceName = messageLog.getDatasourceName();
			String expiryDateField = messageLog.getExpiryDateField();
			String tableName = messageLog.getTableName();
			String keyField = messageLog.getKeyField();
			String typeField = messageLog.getTypeField();
			MessageLogObject mlo = new MessageLogObject(datasourceName, tableName, expiryDateField, keyField, typeField);
			if (!messageLogs.contains(mlo)) {
				messageLogs.add(mlo);
			}
		}
	}

	private List<MessageLogObject> getAllMessageLogs(IbisManager ibisManager) {
		List<MessageLogObject> messageLogs = new ArrayList<>();
		for(IAdapter adapter : ibisManager.getRegisteredAdapters()) {
			for (Receiver<?> receiver: adapter.getReceivers()) {
				collectMessageLogs(messageLogs, receiver.getMessageLog());
			}
			PipeLine pipeline = adapter.getPipeLine();
			for (int i=0; i<pipeline.getPipes().size(); i++) {
				IPipe pipe = pipeline.getPipe(i);
				if (pipe instanceof MessageSendingPipe) {
					MessageSendingPipe msp=(MessageSendingPipe)pipe;
					collectMessageLogs(messageLogs, msp.getMessageLog());
				}
			}
		}
		return messageLogs;
	}

	@IbisDoc({"The number of seconds the database driver will wait for a statement to execute. If the limit is exceeded, a TimeoutException is thrown. 0 means no timeout", "0"})
	public void setQueryTimeout(int i) {
		queryTimeout = i;
	}
}