package edu.hm.dako.chat.client;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.hm.dako.chat.connection.Connection;

public class AdvancedMessageListenerThreadImpl extends SimpleMessageListenerThreadImpl {

	private static Log log = LogFactory.getLog(AdvancedMessageListenerThreadImpl.class);

	public AdvancedMessageListenerThreadImpl(ClientUserInterface userInterface, Connection con,
			SharedClientData sharedData) {
		super(userInterface, con, sharedData);

	}

}
