package edu.hm.dako.chat.client;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.hm.dako.chat.common.ChatPDU;
import edu.hm.dako.chat.connection.Connection;

public class AdvancedMessageListenerThreadImpl extends AbstractMessageListenerThread {

	private static Log log = LogFactory.getLog(AdvancedMessageListenerThreadImpl.class);

	public AdvancedMessageListenerThreadImpl(ClientUserInterface userInterface, Connection con,
			SharedClientData sharedData) {
		super(userInterface, con, sharedData);

	}

	@Override
	protected void chatMessageResponseAction(ChatPDU receivedPdu) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void chatMessageEventAction(ChatPDU receivedPdu) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void loginResponseAction(ChatPDU receivedPdu) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void loginEventAction(ChatPDU receivedPdu) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void logoutEventAction(ChatPDU receivedPdu) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void logoutResponseAction(ChatPDU receivedPdu) {
		// TODO Auto-generated method stub

	}

}
