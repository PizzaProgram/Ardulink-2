package com.github.pfichtner.ardulink.core;

import static com.github.pfichtner.ardulink.core.Pin.analogPin;
import static com.github.pfichtner.ardulink.core.Pin.Type.ANALOG;
import static com.github.pfichtner.ardulink.core.Pin.Type.DIGITAL;
import static com.github.pfichtner.ardulink.core.proto.api.MessageIdHolders.addMessageId;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.zu.ardulink.util.StopWatch;

import com.github.pfichtner.ardulink.core.Connection.Listener;
import com.github.pfichtner.ardulink.core.Connection.ListenerAdapter;
import com.github.pfichtner.ardulink.core.Pin.AnalogPin;
import com.github.pfichtner.ardulink.core.Pin.DigitalPin;
import com.github.pfichtner.ardulink.core.events.AnalogPinValueChangedEvent;
import com.github.pfichtner.ardulink.core.events.DefaultAnalogPinValueChangedEvent;
import com.github.pfichtner.ardulink.core.events.DefaultDigitalPinValueChangedEvent;
import com.github.pfichtner.ardulink.core.events.DefaultRplyEvent;
import com.github.pfichtner.ardulink.core.events.DigitalPinValueChangedEvent;
import com.github.pfichtner.ardulink.core.proto.api.Protocol;
import com.github.pfichtner.ardulink.core.proto.api.Protocol.FromArduino;
import com.github.pfichtner.ardulink.core.proto.impl.DefaultToArduinoNoTone;
import com.github.pfichtner.ardulink.core.proto.impl.FromArduinoPinStateChanged;
import com.github.pfichtner.ardulink.core.proto.impl.FromArduinoReply;

public abstract class AbstractConnectionBasedLink extends AbstractListenerLink {

	private final Connection connection;
	private final Protocol protocol;

	public AbstractConnectionBasedLink(Connection connection, Protocol protocol) {
		this.connection = connection;
		this.protocol = protocol;
		this.connection.addListener(new ListenerAdapter() {
			@Override
			public void received(byte[] bytes) throws IOException {
				AbstractConnectionBasedLink.this.received(bytes);
			}
		});
	}

	public Connection getConnection() {
		return this.connection;
	}

	public Protocol getProtocol() {
		return protocol;
	}

	protected void received(byte[] bytes) {
		FromArduino fromArduino = this.protocol.fromArduino(bytes);
		if (fromArduino instanceof FromArduinoPinStateChanged) {
			handlePinChanged((FromArduinoPinStateChanged) fromArduino);
		} else if (fromArduino instanceof FromArduinoReply) {
			FromArduinoReply reply = (FromArduinoReply) fromArduino;
			fireReplyReceived(new DefaultRplyEvent(reply.isOk(), reply.getId()));
		} else {
			throw new IllegalStateException("Cannot handle " + fromArduino);
		}
	}

	private void handlePinChanged(FromArduinoPinStateChanged pinChanged) {
		Pin pin = pinChanged.getPin();
		Object value = pinChanged.getValue();
		if (pin.is(ANALOG) && value instanceof Integer) {
			AnalogPinValueChangedEvent event = new DefaultAnalogPinValueChangedEvent(
					(AnalogPin) pin, (Integer) value);
			fireStateChanged(event);
		} else if (pin.is(DIGITAL) && value instanceof Boolean) {
			DigitalPinValueChangedEvent event = new DefaultDigitalPinValueChangedEvent(
					(DigitalPin) pin, (Boolean) value);
			fireStateChanged(event);
		} else {
			throw new IllegalStateException(
					"Cannot handle pin change event for pin " + pin
							+ " with value " + value);
		}
	}

	/**
	 * Will wait for the arduino to received the "ready" paket or the arduino to
	 * respond to our messages sent.
	 * 
	 * @param wait
	 *            the maximum time to wait
	 * @param timeUnit
	 *            the units to wait
	 * @return <code>true</code> if the arduino did response within the given
	 *         time otherwise <code>false</code>
	 */
	public boolean waitForArduinoToBoot(int wait, TimeUnit timeUnit) {
		return waitForArduinoToBoot(wait, timeUnit, Mode.ANY_MESSAGE_RECEIVED);
	}

	public enum Mode {
		/**
		 * interpret any message received from arduino to be ok
		 */
		ANY_MESSAGE_RECEIVED, /**
		 * only interpret "ready" packets to be ok
		 */
		READY_MESSAGE_ONLY;
	}

	/**
	 * Will wait for the arduino to received the "ready" paket or the arduino to
	 * respond to our messages sent.
	 * 
	 * @param wait
	 *            the maximum time to wait
	 * @param timeUnit
	 *            the units to wait
	 * @param mode
	 *            the messages to be interpreted as "ok"
	 * 
	 * @return <code>true</code> if the arduino did response within the given
	 *         time otherwise <code>false</code>
	 */
	public boolean waitForArduinoToBoot(int wait, TimeUnit timeUnit, Mode mode) {
		final ReentrantLock lock = new ReentrantLock();
		final Condition condition = lock.newCondition();
		Listener listener = mode == Mode.ANY_MESSAGE_RECEIVED ? signalIfBytesReceived(
				lock, condition) : Listener.NULL;
		this.connection.addListener(listener);

		ConnectionListener cl = new ConnectionListener() {
			@Override
			public void connectionReady() {
				lock.lock();
				try {
					condition.signalAll();
				} finally {
					lock.unlock();
				}
			}

			@Override
			public void connectionLost() {
				// do nothing
			}

			@Override
			public void reconnected() {
				// do nothing
			}
		};
		addConnectionListener(cl);

		StopWatch stopwatch = new StopWatch().start();
		try {
			while (true) {
				lock.lock();
				try {
					ping();
					if (condition.await(500, MILLISECONDS)) {
						return true;
					}
					long time = stopwatch.getTime(timeUnit);
					if (time >= wait) {
						return false;
					}
				} finally {
					lock.unlock();
				}
			}
		} catch (InterruptedException e) {
			return false;
		} finally {
			removeConnectionListener(cl);
			this.connection.removeListener(listener);
		}
	}

	private ListenerAdapter signalIfBytesReceived(final ReentrantLock lock,
			final Condition condition) {
		ListenerAdapter listener = new ListenerAdapter() {
			@Override
			public void received(byte[] bytes) throws IOException {
				lock.lock();
				try {
					condition.signalAll();
				} finally {
					lock.unlock();
				}
			}
		};
		return listener;
	}

	private void ping() {
		// this is not really a ping message since such a message does not exist
		// (yet). So let's write something that the arduino tries to respond to.
		try {
			long messageId = 0;
			connection.write(getProtocol().toArduino(
					addMessageId(new DefaultToArduinoNoTone(analogPin(0)),
							messageId)));
		} catch (IOException e) {
			// ignore
		}
	}

	@Override
	public void close() throws IOException {
		deregisterAllEventListeners();
		this.connection.close();
		super.close();
	}

}
