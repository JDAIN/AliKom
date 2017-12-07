package edu.hm.dako.chat.server;

import edu.hm.dako.chat.common.ChatPDU;
import edu.hm.dako.chat.connection.Connection;

public class AdvancedChatWorkerThreadImpl extends AbstractWorkerThread {

	public AdvancedChatWorkerThreadImpl(Connection con, SharedChatClientList clients, SharedServerCounter counter,
			ChatServerGuiInterface serverGuiInterface) {
		super(con, clients, counter, serverGuiInterface);
		// TODO Auto-generated constructor stub
	}

	@Override
	protected void loginRequestAction(ChatPDU receivedPdu) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void logoutRequestAction(ChatPDU receivedPdu) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void chatMessageRequestAction(ChatPDU receivedPdu) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void handleIncomingMessage() throws Exception {
		// TODO Auto-generated method stub

	}

}
