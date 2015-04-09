package org.amplexus.dfrobot.app.test;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import com.rapplogic.xbee.api.ApiId;
import com.rapplogic.xbee.api.ErrorResponse;
import com.rapplogic.xbee.api.XBee;
import com.rapplogic.xbee.api.XBeeAddress16;
import com.rapplogic.xbee.api.XBeeException;
import com.rapplogic.xbee.api.XBeeResponse;
import com.rapplogic.xbee.api.wpan.RxResponse16;
import com.rapplogic.xbee.api.wpan.RxResponse64;
import com.rapplogic.xbee.api.wpan.TxRequest16;
import com.rapplogic.xbee.api.wpan.TxStatusResponse;
import com.rapplogic.xbee.util.ByteUtils;

/**
 * 
 * XBEE EXPLORER CONFIG - PANID 4545 - MY 8080 - FIRMWARE 10E6 - AP 2 - SH
 * 13A200 - SL 4060F170
 * 
 * XBEE SHIELD CONFIG - PANID 4545 - MY 8081 - FIRMWARE 10E6 - AP 2 - SH 13A200
 * - SL 4060EF99
 * 
 * IMPORTANT: all numeric values are HEX - including MY and PANID
 * 
 * Works with: XBeeDemo.sketch
 * 
 */
public class TestArduinoSendReceiveExample {

	public static final int BAUD_RATE = 9600;
	public static final String USB_PORT = "/dev/ttyUSB0";
	public static final int XBEE_SHIELD_MY_MSB = 0x80;
	public static final int XBEE_SHIELD_MY_LSB = 0x81;
	public static final int PANID = 0x4545;

	private final static Logger log = Logger
			.getLogger(TestArduinoSendReceiveExample.class);

	/**
	 * @param args
	 */
	public static void main(String[] args) throws XBeeException {
		PropertyConfigurator.configure("log4j.properties");

		// replace with port and baud rate of your XBee
		XBee xbee = new XBee();

		log.info("Opening port");
		xbee.open(USB_PORT, BAUD_RATE);

		/*
		 * Send a payload to the XBee shield
		 */

		// Note: we are using the Java int data type, since the byte data type
		// is not unsigned, but the payload is limited to bytes. That is, values
		// must be 0-255.
//		int[] payload = new int[] { 90, 180 };
//
//		// specify the remote XBee 16-bit MY address
//		XBeeAddress16 destination = new XBeeAddress16(XBEE_SHIELD_MY_MSB,
//				XBEE_SHIELD_MY_LSB);
//
//		TxRequest16 tx = new TxRequest16(destination, payload);
//
//		log.info("Sending tx");
//		TxStatusResponse status = (TxStatusResponse) xbee.sendSynchronous(tx);
//		if (status.isSuccess()) {
//			log.info("Succeeded!");
//		} else {
//			log.error("Failed!");
//			xbee.close();
//			return ;
//		}

		/*
		 * Receive a payload from the XBee shield
		 */
		int count = 0;
		int errors = 0;

		while(true)
			try {
				log.info("Getting response");
				XBeeResponse response = xbee.getResponse(200000);
				count++;

				if (response.isError()) {
					log.info("response contains errors", ((ErrorResponse) response).getException());
					errors++;
				}

//				for (int i = 0; i < response.getPacketBytes().length; i++) {
//					log.info("response packet byte[" + i + "]=" + ByteUtils.toBase16(response.getPacketBytes()[i]));
//				}

				if (response.getApiId() == ApiId.RX_16_RESPONSE) {
					log.info("Received RX 16 packet " + ((RxResponse16) response));
				} else if (response.getApiId() == ApiId.RX_64_RESPONSE) {
					log.info("Received RX 64 packet " + ((RxResponse64) response));
				} else {
					log.info("Received mystery packet " + response.toString());
				}

				log.debug("Received response: " + response.toString() + ", count is " + count + ", errors is " + errors);
			} catch (Exception e) {
				log.error("Error getting response: " + e.getClass().getCanonicalName());
				errors++ ;
				if(errors > 5) {
					log.info("Exceeded error count, bailing!");
					break ;
				}
			}
		xbee.close();
	}

}
