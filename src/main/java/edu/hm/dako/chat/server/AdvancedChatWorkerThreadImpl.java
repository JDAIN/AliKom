package edu.hm.dako.chat.server;

import java.util.Vector;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.hm.dako.chat.common.ChatPDU;
import edu.hm.dako.chat.common.ClientConversationStatus;
import edu.hm.dako.chat.common.ClientListEntry;
import edu.hm.dako.chat.common.ExceptionHandler;
import edu.hm.dako.chat.connection.Connection;
import edu.hm.dako.chat.connection.ConnectionTimeoutException;
import edu.hm.dako.chat.connection.EndOfFileException;

/**
 * Advanced Worker-Thread zur serverseitigen Bedienung einer Session mit einem Client. Jedem
 * Chat-Client wird serverseitig ein Worker-Thread zugeordnet.
 * 
 * @author Jannis Ditterich
 */
public class AdvancedChatWorkerThreadImpl extends AbstractWorkerThread {

  private static Log log = LogFactory.getLog(AdvancedChatWorkerThreadImpl.class);

  public AdvancedChatWorkerThreadImpl(Connection con, SharedChatClientList clients,
      SharedServerCounter counter, ChatServerGuiInterface serverGuiInterface) {
    super(con, clients, counter, serverGuiInterface);
  }

  @Override
  public void run() {
    log.debug("ChatWorker-Thread erzeugt, Threadname: " + Thread.currentThread().getName());
    while (!finished && !Thread.currentThread().isInterrupted()) {
      try {
        // Warte auf naechste Nachricht des Clients und fuehre
        // entsprechende Aktion aus
        handleIncomingMessage();
      } catch (Exception e) {
        log.error("Exception waehrend der Nachrichtenverarbeitung");
        ExceptionHandler.logException(e);
      }
    }
    log.debug(Thread.currentThread().getName() + " beendet sich");
    closeConnection();
  }

  /**
   * Senden eines Login-List-Update-Event an alle angemeldeten Clients
   * 
   * @param pdu
   *          Zu sendende PDU
   */
  protected void sendLoginListUpdateEvent(ChatPDU pdu) {

    // Liste der eingeloggten bzw. sich einloggenden User ermitteln
    Vector<String> clientList = clients.getRegisteredClientNameList();

    log.debug("Aktuelle Clientliste, die an die Clients uebertragen wird: " + clientList);

    pdu.setClients(clientList);

    Vector<String> clientList2 = clients.getClientNameList();
    for (String s : new Vector<String>(clientList2)) {
      log.debug(
          "Fuer " + s + " wird Login- oder Logout-Event-PDU an alle aktiven Clients gesendet");

      ClientListEntry client = clients.getClient(s);
      try {
        if (client != null) {

          client.getConnection().send(pdu);
          log.debug("Login- oder Logout-Event-PDU an " + client.getUserName() + " gesendet");
          clients.incrNumberOfSentChatEvents(client.getUserName());
          eventCounter.getAndIncrement();
        }
      } catch (Exception e) {
        log.error("Senden einer Login- oder Logout-Event-PDU an " + s + " nicht moeglich");
        ExceptionHandler.logException(e);
      }
    }
  }

  @Override
  protected void loginRequestAction(ChatPDU receivedPdu) {

    ChatPDU pdu;
    // advanced: warteliste erstellen für login
    clients.createWaitList(receivedPdu.getUserName());

    // Neuer Client moechte sich einloggen, Client in Client-Liste
    // eintragen
    if (!clients.existsClient(receivedPdu.getUserName())) {
      log.debug("User nicht in Clientliste: " + receivedPdu.getUserName());
      ClientListEntry client = new ClientListEntry(receivedPdu.getUserName(), connection);
      client.setLoginTime(System.nanoTime());
      clients.createClient(receivedPdu.getUserName(), client);
      clients.changeClientStatus(receivedPdu.getUserName(), ClientConversationStatus.REGISTERING);
      log.debug("User " + receivedPdu.getUserName() + " nun in Clientliste");

      userName = receivedPdu.getUserName();
      clientThreadName = receivedPdu.getClientThreadName();
      Thread.currentThread().setName(receivedPdu.getUserName());
      log.debug("Laenge der Clientliste: " + clients.size());
      serverGuiInterface.incrNumberOfLoggedInClients();

      // Login-Event an alle Clients (auch an den gerade aktuell
      // anfragenden) senden

      Vector<String> clientList = clients.getClientNameList();
      pdu = ChatPDU.createLoginEventPdu(userName, clientList, receivedPdu);
      sendLoginListUpdateEvent(pdu);

      log.debug("\n  \n loginrequestaction vor change  \n  \n" + clients.getClientStatus(userName)
          + "\n" + "\n" + "\n" + receivedPdu.toString() + "\n \n \n" + pdu.toString() + "\n \n");

      log.debug("\n  \n loginrequestaction nach change  \n  \n" + clients.getClientStatus(userName)
          + "\n \n");
    } else {
      // User bereits angemeldet, Fehlermeldung an Client senden,
      // Fehlercode an Client senden
      pdu = ChatPDU.createLoginErrorResponsePdu(receivedPdu, ChatPDU.LOGIN_ERROR);

      try {
        connection.send(pdu);
        log.debug("Login-Response-PDU an " + receivedPdu.getUserName() + " mit Fehlercode "
            + ChatPDU.LOGIN_ERROR + " gesendet");
      } catch (Exception e) {
        log.debug(
            "Senden einer Login-Response-PDU an " + receivedPdu.getUserName() + " nicht moeglich");
        ExceptionHandler.logExceptionAndTerminate(e);
      }
    }
  }

  @Override
  protected void logoutRequestAction(ChatPDU receivedPdu) {

    ChatPDU pdu;
    logoutCounter.getAndIncrement();
    log.debug("Logout-Request von " + receivedPdu.getUserName() + ", LogoutCount = "
        + logoutCounter.get());

    log.debug("Logout-Request-PDU von " + receivedPdu.getUserName() + " empfangen");

    if (!clients.existsClient(userName)) {
      log.debug("User nicht in Clientliste: " + receivedPdu.getUserName());
    } else {

      // Event an Client versenden
      Vector<String> clientList = clients.getClientNameList();
      pdu = ChatPDU.createLogoutEventPdu(userName, clientList, receivedPdu);

      clients.createWaitList(userName);
      clients.changeClientStatus(receivedPdu.getUserName(), ClientConversationStatus.UNREGISTERING);
      sendLoginListUpdateEvent(pdu);
      serverGuiInterface.decrNumberOfLoggedInClients();

      // advanced: warteliste erstellen für logout
      log.debug("\n \n logutoutrequestaction waitlist \n " + clients.printClientList());

    }
  }

  @Override
  protected void chatMessageRequestAction(ChatPDU receivedPdu) {

    ClientListEntry client = null;
    clients.setRequestStartTime(receivedPdu.getUserName(), startTime);
    clients.incrNumberOfReceivedChatMessages(receivedPdu.getUserName());
    serverGuiInterface.incrNumberOfRequests();
    log.debug("Chat-Message-Request-PDU von " + receivedPdu.getUserName() + " mit Sequenznummer "
        + receivedPdu.getSequenceNumber() + " empfangen");

    if (!clients.existsClient(receivedPdu.getUserName())) {
      log.debug("User nicht in Clientliste: " + receivedPdu.getUserName());
    } else {
      // Liste der betroffenen Clients ermitteln
      Vector<String> sendList = clients.getClientNameList();
      ChatPDU pdu = ChatPDU.createChatMessageEventPdu(userName, receivedPdu);

      // advanced warteliste erstellen
      clients.createWaitList(receivedPdu.getUserName());

      // Event an Clients senden
      for (String s : new Vector<String>(sendList)) {
        client = clients.getClient(s);
        try {
          if ((client != null) && (client.getStatus() != ClientConversationStatus.UNREGISTERED)) {
            pdu.setUserName(client.getUserName());
            client.getConnection().send(pdu);
            log.debug("Chat-Event-PDU an " + client.getUserName() + " gesendet");
            clients.incrNumberOfSentChatEvents(client.getUserName());
            eventCounter.getAndIncrement();
            log.debug(userName + ": EventCounter erhoeht = " + eventCounter.get()
                + ", Aktueller ConfirmCounter = " + confirmCounter.get()
                + ", Anzahl gesendeter ChatMessages von dem Client = "
                + receivedPdu.getSequenceNumber());
          }
        } catch (Exception e) {
          log.debug("Senden einer Chat-Event-PDU an " + client.getUserName() + " nicht moeglich");
          ExceptionHandler.logException(e);
        }
      }

      log.debug("Aktuelle Laenge der Clientliste: " + clients.size());
    }
  }

  /**
   * Verbindung zu einem Client ordentlich abbauen.
   */
  private void closeConnection() {

    log.debug("Schliessen der Chat-Connection zum " + userName);

    // Bereinigen der Clientliste falls erforderlich

    if (clients.existsClient(userName)) {
      log.debug("Close Connection fuer " + userName
          + ", Laenge der Clientliste vor dem bedingungslosen Loeschen: " + clients.size());

      clients.deleteClientWithoutCondition(userName);
      log.debug("Laenge der Clientliste nach dem bedingungslosen Loeschen von " + userName + ": "
          + clients.size());
    }

    try {
      connection.close();
    } catch (Exception e) {
      log.error("Exception bei close");
      // ExceptionHandler.logException(e);
    }
  }

  /**
   * Antwort-PDU fuer den initiierenden Client aufbauen und senden
   * 
   * @param eventInitiatorClient
   *          Name des Clients
   */
  private void sendLogoutResponse(String eventInitiatorClient) {

    ClientListEntry client = clients.getClient(eventInitiatorClient);

    if (client != null) {
      ChatPDU responsePdu = ChatPDU.createLogoutResponsePdu(eventInitiatorClient, 0, 0, 0, 0,
          client.getNumberOfReceivedChatMessages(), clientThreadName);

      log.debug(
          eventInitiatorClient + ": SentEvents aus Clientliste: " + client.getNumberOfSentEvents()
              + ": ReceivedConfirms aus Clientliste: " + client.getNumberOfReceivedEventConfirms());
      try {
        clients.getClient(eventInitiatorClient).getConnection().send(responsePdu);
      } catch (Exception e) {
        log.debug(
            "Senden einer Logout-Response-PDU an " + eventInitiatorClient + " fehlgeschlagen");
        log.debug("Exception Message: " + e.getMessage());
      }

      log.debug("Logout-Response-PDU an Client " + eventInitiatorClient + " gesendet");
    }
  }

  /**
   * Prueft, ob Clients aus der Clientliste geloescht werden koennen.
   * 
   * @return boolean, true: Client geloescht, false: Client nicht geloescht
   */
  private boolean checkIfClientIsDeletable() {

    ClientListEntry client;

    // Worker-Thread beenden, wenn sein Client schon abgemeldet ist
    if (userName != null) {
      client = clients.getClient(userName);
      if (client != null) {
        if (client.isFinished()) {
          // Loesche den Client aus der Clientliste
          // Ein Loeschen ist aber nur zulaessig, wenn der Client
          // nicht mehr in einer anderen Warteliste ist
          log.debug(
              "Laenge der Clientliste vor dem Entfernen von " + userName + ": " + clients.size());
          if (clients.deleteClient(userName) == true) {
            // Jetzt kann auch Worker-Thread beendet werden

            log.debug("Laenge der Clientliste nach dem Entfernen von " + userName + ": "
                + clients.size());
            log.debug("Worker-Thread fuer " + userName + " zum Beenden vorgemerkt");
            return true;
          }
        }
      }
    }

    // Garbage Collection in der Clientliste durchfuehren
    Vector<String> deletedClients = clients.gcClientList();
    if (deletedClients.contains(userName)) {
      log.debug("Ueber Garbage Collector ermittelt: Laufender Worker-Thread fuer " + userName
          + " kann beendet werden");
      finished = true;
      return true;
    }
    return false;
  }

  @Override
  protected void handleIncomingMessage() throws Exception {
    if (checkIfClientIsDeletable() == true) {
      return;
    }

    // Warten auf naechste Nachricht
    ChatPDU receivedPdu = null;

    // Nach einer Minute wird geprueft, ob Client noch eingeloggt ist
    final int RECEIVE_TIMEOUT = 1200000;

    try {
      receivedPdu = (ChatPDU) connection.receive(RECEIVE_TIMEOUT);
      // Nachricht empfangen
      // Zeitmessung fuer Serverbearbeitungszeit starten
      startTime = System.nanoTime();

    } catch (ConnectionTimeoutException e) {

      // Wartezeit beim Empfang abgelaufen, pruefen, ob der Client
      // ueberhaupt noch etwas sendet
      log.debug("Timeout beim Empfangen, " + RECEIVE_TIMEOUT + " ms ohne Nachricht vom Client");

      if (clients.getClient(userName) != null) {
        if (clients.getClient(userName).getStatus() == ClientConversationStatus.UNREGISTERING) {
          // Worker-Thread wartet auf eine Nachricht vom Client, aber
          // es
          // kommt nichts mehr an
          log.error("Client ist im Zustand UNREGISTERING und bekommt aber keine Nachricht mehr");
          // Zur Sicherheit eine Logout-Response-PDU an Client senden
          // und
          // dann Worker-Thread beenden
          finished = true;
        }
      }
      return;

    } catch (EndOfFileException e) {
      log.debug(
          "End of File beim Empfang, vermutlich Verbindungsabbau des Partners fuer " + userName);
      finished = true;
      return;

    } catch (java.net.SocketException e) {
      log.error("Verbindungsabbruch beim Empfang der naechsten Nachricht vom Client " + getName());
      finished = true;
      return;

    } catch (Exception e) {
      log.error("Empfang einer Nachricht fehlgeschlagen, Workerthread fuer User: " + userName);
      ExceptionHandler.logException(e);
      finished = true;
      return;
    }

    // Empfangene Nachricht bearbeiten
    try {
      switch (receivedPdu.getPduType()) {

      case LOGIN_REQUEST:
        // Login-Request vom Client empfangen
        loginRequestAction(receivedPdu);
        break;

      case CHAT_MESSAGE_REQUEST:
        // Chat-Nachricht angekommen, an alle verteilen
        chatMessageRequestAction(receivedPdu);
        break;

      case LOGOUT_REQUEST:
        // Logout-Request vom Client empfangen
        logoutRequestAction(receivedPdu);
        break;

      case MESSAGE_CONFIRM: // advanced
        chatMessageConfirmAction(receivedPdu);
        break;

      case LOGIN_CONFIRM: // advanced
        loginConfirmAction(receivedPdu);
        break;

      case LOGOUT_CONFIRM: // advanced
        logoutConfirmAction(receivedPdu);
        break;

      default:
        log.debug("Falsche PDU empfangen von Client: " + receivedPdu.getUserName() + ", PduType: "
            + receivedPdu.getPduType());
        break;
      }
    } catch (Exception e) {
      log.error("Exception bei der Nachrichtenverarbeitung");
      ExceptionHandler.logExceptionAndTerminate(e);
    }
  }

  /**
   * Aktion fuer die Behandlung ankommender Logout-Confirms: Nach erhaltung aller Confirms, Client
   * Response an Eventinitiator schicken.
   * 
   * @param receivedPdu
   *          Empfangenes Pdu
   */
  private void logoutConfirmAction(ChatPDU receivedPdu) {
    log.debug("LogoutConfirm empfangen von Client" + receivedPdu.getUserName() + " zu Logout von "
        + receivedPdu.getEventUserName());

    confirmCounter.incrementAndGet(); // fuer testausgaben
    // tatsächliche erhöhung
    clients.incrNumberOfReceivedChatEventConfirms(receivedPdu.getEventUserName());

    try {
      log.debug("\n vor Löschung \n " + clients.printClientList());
      clients.deleteWaitListEntry(receivedPdu.getEventUserName(), receivedPdu.getUserName());
      log.debug("\n nach Löschung \n " + clients.printClientList());
    } catch (Exception e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    // wenn warteliste leer ist LoginResponse an Event initiator senden
    if (clients.getWaitListSize(receivedPdu.getEventUserName()) == 0) {

      clients.changeClientStatus(receivedPdu.getEventUserName(),
          ClientConversationStatus.UNREGISTERED);

      // Logout Response senden
      sendLogoutResponse(receivedPdu.getEventUserName()); // etv event
      log.debug("sendlogoutresponsepdu: " + receivedPdu.toString());
      // Worker-Thread des Clients, der den Logout-Request gesendet
      // hat, auch gleich zum Beenden markieren
      clients.finish(receivedPdu.getEventUserName());
      log.debug("Laenge der Clientliste beim Vormerken zum Loeschen von "
          + receivedPdu.getUserName() + ": " + clients.size());
    }
  }

  /**
   * Aktion fuer die Behandlung ankommender Logout-Confirms: Nach erhaltung aller Confirms, Client
   * Response an Eventinitiator schicken.
   * 
   * @param receivedPdu
   *          Empfangenes Pdu
   */
  private void loginConfirmAction(ChatPDU receivedPdu) {
    log.debug("LoginConfirm empfangen von Client" + receivedPdu.getUserName() + " zu Login von "
        + receivedPdu.getEventUserName());

    confirmCounter.incrementAndGet(); // fuer testausgaben
    // tatsächliche erhöhung
    clients.incrNumberOfReceivedChatEventConfirms(receivedPdu.getEventUserName());

    try {
      clients.deleteWaitListEntry(receivedPdu.getEventUserName(), receivedPdu.getUserName());

    } catch (Exception e) {
      log.error("Löschen der Waitlist im Login fehlgeschlagen" + e);
      e.printStackTrace();
    }

    // wenn warteliste leer ist LoginResponse an Event initiator senden
    if (clients.getWaitListSize(receivedPdu.getEventUserName()) == 0) {
      // Login Response senden
      ChatPDU responsePdu = ChatPDU.createLoginResponsePdu(receivedPdu.getEventUserName(),
          receivedPdu);
      responsePdu.toString();
      try {
        clients.getClient(userName).getConnection().send(responsePdu);
        log.debug("Login-Response-PDU an Client " + userName + " gesendet");
      } catch (Exception e) {
        log.debug("Senden einer Login-Response-PDU an " + userName + " fehlgeschlagen");
        log.debug("Exception Message: " + e.getMessage());
      }

      clients.changeClientStatus(userName, ClientConversationStatus.REGISTERED);

    }
  }

  @Override
  protected void chatMessageConfirmAction(ChatPDU receivedPdu) {

    log.debug("ChatMessageConfirm empfangen von Client" + receivedPdu.getUserName()
        + " zu Nachricht von " + receivedPdu.getEventUserName());

    confirmCounter.incrementAndGet(); // fuer testausgaben
    // tatsächliche erhöhung
    clients.incrNumberOfReceivedChatEventConfirms(receivedPdu.getEventUserName());

    try {

      clients.deleteWaitListEntry(receivedPdu.getEventUserName(), receivedPdu.getUserName());
    } catch (Exception e1) {
      log.error("Löschen der Waitlist im Login fehlgeschlagen");
      e1.printStackTrace();
    }

    if (clients.getWaitListSize(receivedPdu.getEventUserName()) == 0) {
      // nur aus ChatmessageRequestACtion kopiert
      ClientListEntry client = null;

      // erstellen des ChatMessageResponse
      client = clients.getClient(receivedPdu.getEventUserName());
      if (client != null) {
        ChatPDU responsePdu = ChatPDU.createChatMessageResponsePdu(receivedPdu.getUserName(), 0, 0,
            0, 0, client.getNumberOfReceivedChatMessages(), receivedPdu.getClientThreadName(),
            (System.nanoTime() - client.getStartTime()));

        if (responsePdu.getServerTime() / 1000000 > 100) {
          log.debug(Thread.currentThread().getName()
              + ", Benoetigte Serverzeit vor dem Senden der Response-Nachricht > 100 ms: "
              + responsePdu.getServerTime() + " ns = " + responsePdu.getServerTime() / 1000000
              + " ms");
        }

        // erst senden wenn von jedem confirm da
        // senden des ChatMessageResponse
        // clientlistentry waitList methoden dazu unten
        try {
          client.getConnection().send(responsePdu);
          log.debug("Chat-Message-Response-PDU an " + receivedPdu.getUserName() + " gesendet");
        } catch (Exception e) {
          log.debug("Senden einer Chat-Message-Response-PDU an " + client.getUserName()
              + " nicht moeglich");
          ExceptionHandler.logExceptionAndTerminate(e);
        }
      }
      log.debug("Aktuelle Laenge der Clientliste: " + clients.size());
    }
  }
}
