/*
   Copyright 2013, 2016 Nationale-Nederlanden, 2022-2023 WeAreFrank!

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
package nl.nn.adapterframework.senders;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

import nl.nn.adapterframework.core.PipeLineSession;
import nl.nn.adapterframework.core.RequestReplyExecutor;
import nl.nn.adapterframework.core.SenderResult;
import nl.nn.adapterframework.receivers.ServiceClient;
import nl.nn.adapterframework.stream.Message;
import nl.nn.adapterframework.stream.ThreadConnector;
import nl.nn.adapterframework.stream.ThreadLifeCycleEventListener;
import nl.nn.adapterframework.util.Guard;
import nl.nn.adapterframework.util.LogUtil;

public class IsolatedServiceExecutor extends RequestReplyExecutor {
	private final Logger log = LogUtil.getLogger(this);
	private final ServiceClient service;
	private final PipeLineSession session;
	private final Guard guard;
	private final ThreadConnector<?> threadConnector;

	public IsolatedServiceExecutor(ServiceClient service, Message message, PipeLineSession session, Guard guard, ThreadLifeCycleEventListener<?> threadLifeCycleEventListener) {
		super();
		this.service = service;
		this.request = message;
		this.session = session;
		this.guard = guard;
		this.threadConnector = new ThreadConnector<>(this, "IsolatedServiceExecutor", threadLifeCycleEventListener, null, session);
	}

	@Override
	public void run() {
		try (ThreadConnector<?> threadConnector = this.threadConnector) {
			threadConnector.startThread(request);
			Message result = service.processRequest(request, session);
			reply = new SenderResult(threadConnector.endThread(result));
		} catch (Throwable t) {
			log.warn("IsolatedServiceCaller caught exception",t);
			throwable = threadConnector.abortThread(t);
		} finally {
			ThreadContext.clearAll();
			if (guard != null) {
				guard.releaseResource();
			}
		}
	}
}
