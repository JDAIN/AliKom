package edu.hm.dako.chat.client;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.hm.dako.chat.common.ChatPDU;
import edu.hm.dako.chat.common.ClientConversationStatus;
import edu.hm.dako.chat.common.ExceptionHandler;
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
	
	
	
	
	
	

	@Override // unverändert aus Simple
	protected void loginResponseAction(ChatPDU receivedPdu) {

		if (receivedPdu.getErrorCode() == ChatPDU.LOGIN_ERROR) {

			// Login hat nicht funktioniert
			log.error("Login-Response-PDU fuer Client " + receivedPdu.getUserName() + " mit Login-Error empfangen");
			userInterface.setErrorMessage("Chat-Server", "Anmelden beim Server nicht erfolgreich, Benutzer "
					+ receivedPdu.getUserName() + " vermutlich schon angemeldet", receivedPdu.getErrorCode());
			sharedClientData.status = ClientConversationStatus.UNREGISTERED;

			// Verbindung wird gleich geschlossen
			try {
				connection.close();
			} catch (Exception e) {
			}

		} else {
			// Login hat funktioniert
			sharedClientData.status = ClientConversationStatus.REGISTERED;

			userInterface.loginComplete();

			Thread.currentThread().setName("Listener" + "-" + sharedClientData.userName);
			log.debug("Login-Response-PDU fuer Client " + receivedPdu.getUserName() + " empfangen");
		}
	}

	@Override
	protected void loginEventAction(ChatPDU receivedPdu) {
		// Eventzaehler fuer Testzwecke erhoehen
		sharedClientData.eventCounter.getAndIncrement();
		int events = SharedClientData.loginEvents.incrementAndGet();

		log.debug(sharedClientData.userName + " erhaelt LoginEvent, LoginEventCounter: " + events);

		try {
			handleUserListEvent(receivedPdu);
		} catch (Exception e) {
			ExceptionHandler.logException(e);
		}
	}
	
	
	
	
	
	
	
	
	

	@Override
	protected void logoutEventAction(ChatPDU receivedPdu) {
		// Eventzaehler fuer Testzwecke erhoehen
		sharedClientData.eventCounter.getAndIncrement();
		int events = SharedClientData.logoutEvents.incrementAndGet();

		log.debug("LogoutEventCounter: " + events);

		try {
			handleUserListEvent(receivedPdu);
		} catch (Exception e) {
			ExceptionHandler.logException(e);
		}
	}

	@Override
	protected void logoutResponseAction(ChatPDU receivedPdu) {
		log.debug(
				sharedClientData.userName + " empfaengt Logout-Response-PDU fuer Client " + receivedPdu.getUserName());
		sharedClientData.status = ClientConversationStatus.UNREGISTERED;

		userInterface.setSessionStatisticsCounter(sharedClientData.eventCounter.longValue(),
				sharedClientData.confirmCounter.longValue(), 0, 0, 0);

		log.debug("Vom Client gesendete Chat-Nachrichten:  " + sharedClientData.messageCounter.get());

		finished = true;
		userInterface.logoutComplete();
	}

}
