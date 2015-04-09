package org.amplexus.dfrobot.app.test;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import com.rapplogic.xbee.api.ApiId;
import com.rapplogic.xbee.api.ErrorResponse;
import com.rapplogic.xbee.api.XBee;
import com.rapplogic.xbee.api.XBeeAddress16;
import com.rapplogic.xbee.api.XBeeException;
import com.rapplogic.xbee.api.XBeeResponse;
import com.rapplogic.xbee.api.XBeeTimeoutException;
import com.rapplogic.xbee.api.wpan.RxResponse16;
import com.rapplogic.xbee.api.wpan.RxResponse64;
import com.rapplogic.xbee.api.wpan.TxRequest16;
import com.rapplogic.xbee.api.wpan.TxStatusResponse;
import com.rapplogic.xbee.util.ByteUtils;

/**
 * Arduino sketch: xbee_arduino_usecase_pc_to_arduino_working_sketch
 * 
 * @author craig
 *
 */
public class TestSendToArduino {

	public static final int XBEE_SHIELD_MY_MSB = 0x80; // The MY address MSB of the XBee we are talking to
	public static final int XBEE_SHIELD_MY_LSB = 0x81; // The MY address LSB of the XBee we are talking to

	private final static Logger log = Logger.getLogger(TestSendToArduino.class);

	public static void main(String [] args) throws XBeeTimeoutException, XBeeException {
		PropertyConfigurator.configure("log4j.properties");

		XBee xbee = new XBee();

		// replace with port and baud rate of your XBee
		xbee.open("/dev/ttyUSB0", 9600);

		try {
			/*
			 * Send request
			 */
			int[] payload = new int[] { 90, 180 };
	
			// specify the remote XBee 16-bit MY address
			log.info("Sending payload") ;
			XBeeAddress16 destination = new XBeeAddress16(XBEE_SHIELD_MY_MSB, XBEE_SHIELD_MY_LSB);
	
			TxRequest16 tx = new TxRequest16(destination, payload);
	
			TxStatusResponse status = (TxStatusResponse) xbee.sendSynchronous(tx);
	
			if (status.isSuccess()) {
				log.info("Success!") ;
			        // the Arduino XBee received our packet
			} else {
				log.error("Failed: " + status.getStatus().getValue()) ;
			}
			try {
				Thread.sleep(1000) ;
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
			/*
			 * Receive response
			 */
	
			XBeeResponse response = xbee.getResponse(1000);
	
			if (response.isError()) {
				log.error("response contains errors", ((ErrorResponse) response).getException());
			}
	
			for (int i = 0; i < response.getPacketBytes().length; i++) {
				log.info("response packet byte[" + i + "]=" + ByteUtils.toBase16(response.getPacketBytes()[i]));
			}
	
			if (response.getApiId() == ApiId.RX_16_RESPONSE) {
				log.info("Received RX 16 packet " + ((RxResponse16) response));
			} else if (response.getApiId() == ApiId.RX_64_RESPONSE) {
				log.info("Received RX 64 packet " + ((RxResponse64) response));
			} else {
				log.info("Received mystery packet " + response.toString());
			}
	
			/*
			 * Cleanup
			 */
		} finally {
			if(xbee.isConnected())
				xbee.close() ;
		}
	}
}
