package org.amplexus.dfrobot.app.test;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import com.rapplogic.xbee.api.ApiId;
import com.rapplogic.xbee.api.XBee;
import com.rapplogic.xbee.api.XBeeAddress16;
import com.rapplogic.xbee.api.XBeePacket;
import com.rapplogic.xbee.api.XBeeResponse;
import com.rapplogic.xbee.api.wpan.TxRequest16;
import com.rapplogic.xbee.api.wpan.TxRequest64;
import com.rapplogic.xbee.api.wpan.TxStatusResponse;

/**
 * Sends a TX Request every 5000 ms and waits for TX status packet. If the radio
 * is sending samples it will continue to wait for tx status.
 * 
 * @author andrew
 * 
 */
public class TestApiSenderExample {

	public static final int XBEE_SHIELD_MY_MSB = 0x80; // The MY address MSB of the XBee we are talking to
	public static final int XBEE_SHIELD_MY_LSB = 0x81; // The MY address LSB of the XBee we are talking to

	private final static Logger log = Logger.getLogger(TestApiSenderExample.class);

	private TestApiSenderExample() throws Exception {

		XBee xbee = new XBee();

		final int sleep = 5000;

		int count = 0;
		int errors = 0;
		int ackErrors = 0;
		int ccaErrors = 0;
		int purgeErrors = 0;

		long now;

		try {
			// replace with port and baud rate of your XBee
			xbee.open("/dev/ttyUSB0", 9600);

			while (true) {

				// log.debug("Sending count " + count);
				// XBeeResponse response = xbee.sendTxRequest16(destination, 0x0a, payload);

				// int[] payload = new int[] {1,2,3,4,5,6,7,8};
				// to verify correct byte escaping, we'll send a start byte
				int[] payload = new int[] { XBeePacket.SpecialByte.START_BYTE.getValue() };

				// specify the remote XBee 16-bit MY address
				XBeeAddress16 destination = new XBeeAddress16(XBEE_SHIELD_MY_MSB, XBEE_SHIELD_MY_LSB);
				TxRequest16 tx = new TxRequest16(destination, payload);
				// or send a TX64 (same thing except we are addressing by SH+SL
				// address)
				// XBeeAddress64 destination = new XBeeAddress64(0, 0x13, 0xa2,
				// 0, 0x40, 0x08, 0xb4, 0x8f);
				// TxRequest64 tx2 = new TxRequest64(destination64, payload);

				now = System.currentTimeMillis();
				xbee.sendAsynchronous(tx);

				XBeeResponse response = null;

				while (true) {
					// blocks until we get response
					response = xbee.getResponse();

					if (response.getApiId() != ApiId.TX_STATUS_RESPONSE) {
						log.debug("expected tx status but received " + response);
					} else {
						// log.debug("got tx status");

						if (((TxStatusResponse) response).getFrameId() != tx.getFrameId()) {
							throw new RuntimeException("frame id does not match");
						}

						if (((TxStatusResponse) response).getStatus() != TxStatusResponse.Status.SUCCESS) {
							errors++;

							if (((TxStatusResponse) response).isAckError()) {
								ackErrors++;
							} else if (((TxStatusResponse) response).isCcaError()) {
								ccaErrors++;
							} else if (((TxStatusResponse) response).isPurged()) {
								purgeErrors++;
							}

							log.error("Tx status failure with status: " + ((TxStatusResponse) response).getStatus());
						} else {
							// success
							log.debug("Success.  count is " + count
									+ ", errors is " + errors + ", in "
									+ (System.currentTimeMillis() - now)
									+ ", ack errors " + ackErrors
									+ ", ccaErrors " + ccaErrors
									+ ", purge errors " + purgeErrors);
						}

						count++;

						break;
					}
				}

				Thread.sleep(sleep);
			}
		} finally {
			xbee.close();
		}
	}

	public static void main(String[] args) throws Exception {
		// init log4j
		PropertyConfigurator.configure("log4j.properties");
		new TestApiSenderExample();
	}
}