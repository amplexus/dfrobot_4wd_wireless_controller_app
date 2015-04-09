package org.amplexus.dfrobot.app;

import java.util.List;

import javax.swing.SwingWorker;

import org.apache.log4j.Logger;

import com.rapplogic.xbee.api.XBee;
import com.rapplogic.xbee.api.XBeeAddress16;
import com.rapplogic.xbee.api.XBeeException;
import com.rapplogic.xbee.api.XBeeTimeoutException;
import com.rapplogic.xbee.api.wpan.TxRequest16;
import com.rapplogic.xbee.api.wpan.TxStatusResponse;

/**
*   ATSL to get the low bits.
 *   
 * PROTOCOL DATA PC TO XBEE: 2 x bytes where first byte is a command and the second byte is data
 * - motor stop:			byte 1 = 00, byte 2 = N/A
 * - motor forward:			byte 1 = 01, byte 2 = speed (0-255)
 * - motor backward:		byte 1 = 02, byte 2 = speed (0-255)
 * - motor left:			byte 1 = 03, byte 2 = speed (0-255)
 * - motor right:			byte 1 = 04, byte 2 = speed (0-255)
 * - pan-tilt left:			byte 1 = 05, byte 2 = N/A																					# NOT YET SUPPORTED 
 * - pan-tilt right:		byte 1 = 06, byte 2 = N/A																					# NOT YET SUPPORTED
 * - pan-tilt up:			byte 1 = 07, byte 2 = N/A																					# NOT YET SUPPORTED
 * - pan-tilt down:			byte 1 = 08, byte 2 = N/A																					# NOT YET SUPPORTED
 * - ping					byte 1 = 09, byte 2 = N/A																					# NOT YET SUPPORTED
 * - camera zoom:			byte 1 = 10, byte 2 = amount (-127 to 128), where negative is zoom out and positive is zoom in				# NOT YET SUPPORTED
 * - camera picture:		byte 1 = 11, byte 2 = quality (0-255) where 0 is lowest and 255 is highest									# NOT YET SUPPORTED
 * - video stream start:	byte 1 = 12, byte 2 = quality (0-255) where 0 is lowest and 255 is highest									# NOT YET SUPPORTED
 * - video stream stop:		byte 1 = 13, byte 2 = N/A																					# NOT YET SUPPORTED
 * - infrared poll:			byte 1 = 14, byte 2 = sensor to poll (0, 1 or 2)															# NOT YET SUPPORTED
 * - infrared sample start:	byte 1 = 15, byte 2 = poll interval (10-255) x 10 millis - ie 50 = 500 millis. if < 10 ignored				# NOT YET SUPPORTED 
 * - infrared sample stop:	byte 1 = 16, byte 2 = N/A	
 *																			# NOT YET SUPPORTED
 * PROTOCOL LOGIC PC TO XBEE
 *	- startup()
 *		- XBee.open(...)
 *		- flushPending()
 *	- motor<Dir>Command(speed) # where <Dir> is one of Left, Right, Forward, Backwards, Stop
 *		- flushPending()
 *		- ack = false 
 *  	- while(!ack)
 * 			- sendMotor<Dir>(speed)
 *  		- ack = getAck()
 *	- ping()
 *		- flushPending()
 *		- ack = gotPingResponse = false 
 *  	- while(!ack && !gotResponse)
 * 			- sendPing()
 *  		- ack = getAck()
 *  		- gotPingResponse = getResponse(50)
 *	- flushPending()
 * 		- while pending incoming requests
 *   		- read and ignore
 * 	- shutdown()
 * 		- XBee.close()
 * 
 * @author craig
 */
class XBeeCommunicatorTask extends SwingWorker<Integer, Integer> {
	/*
	 * Networking info
	 */
	public static final int PANID				= 0x4545;	// The network we communicate on
	public static final int XBEE_SHIELD_MY_MSB	= 0x80;		// The MY address MSB of the XBee we are talking to
	public static final int XBEE_SHIELD_MY_LSB	= 0x81;		// The MY address LSB of the XBee we are talking to
	/*
	 * Commands we send to the robot
	 */
	public static final int CMD_MOTOR_STOP			= 0 ;
	public static final int CMD_MOTOR_FORWARD		= 1 ;
	public static final int CMD_MOTOR_BACKWARDS		= 2 ;
	public static final int CMD_MOTOR_LEFT			= 3 ;
	public static final int CMD_MOTOR_RIGHT			= 4 ;
	public static final int CMD_PANTILT_LEFT		= 5 ;
	public static final int CMD_PANTILT_RIGHT		= 6 ;
	public static final int CMD_PANTILT_UP			= 7 ;
	public static final int CMD_PANTILT_DOWN		= 8 ;
	public static final int CMD_PING				= 9 ;
	public static final int CMD_AUTONOMOUS_MODE_ON	= 10 ;
	public static final int CMD_AUTONOMOUS_MODE_OFF	= 11 ;
	/*
	 * Stringified command names
	 */
	public static final String[] commandName = {
		"MOTOR STOP",
		"MOTOR FORWARD",
		"MOTOR BACKWARDS",
		"MOTOR LEFT",
		"MOTOR RIGHT",
		"PAN LEFT",
		"PAN RIGHT",
		"PAN UP",
		"PAN DOWN",
		"PING",
		"AUTO ON",
		"AUTO OFF",
	} ;
    private XBee xbee = new XBee(); // We communicate with the robot via the XBee api
    
    /*
     * All the information pertaining to the command we are executing in this task
     */
    private int command ;							// The command (CMD_*) we are executing in this task 
    private int baudRate ;							// The BAUD rate we communicate at over the USB port (commPort)
	private int data ; 								// For the movement commands, data is speed (0-255). Otherwise not used
	private String commPort ;						// The USB port we communicate over
	private DFRobot4WDPlatformController gui ;		// We might need to update widgets in the GUI
	private String lastError = null ;				// If there was an error, the message goes here
    private final static Logger log = Logger.getLogger(XBeeCommunicatorTask.class);
	
    /**
	 * Constructor.
	 */
	public XBeeCommunicatorTask(int command, int data, String commPort, int baudRate, DFRobot4WDPlatformController gui) {
		this.command = command ;
		this.commPort = commPort ;
		this.data = data ;
		this.baudRate = baudRate ;
		this.gui = gui ;
	}
	
	/**
	 * Make this private to force use of the parameterised constructor above.
	 */
	private XBeeCommunicatorTask() {
		
	}
	
	/**
	 * Execute the command specified in the constructor.
	 * 
	 * Opens a communications channel to the XBee explorer, issues the command and closes the channel.
	 * 
	 * Performs the work in a separate task.
	 */
	@Override
	protected Integer doInBackground() throws Exception {
		log.info("Executing command: " + stringifiedCommandName(command)) ;
		try {
			xbee.open(commPort, baudRate);
			switch(command) {
			case CMD_MOTOR_BACKWARDS: 
				motorBackwards() ;
				break ;
			case CMD_MOTOR_FORWARD:
				motorForward() ;
				break ;
			case CMD_MOTOR_LEFT:
				motorLeft() ;
				break ;
			case CMD_MOTOR_RIGHT:
				motorRight() ;
				break ;
			case CMD_MOTOR_STOP:
				motorStop() ;
				break ;
			case CMD_AUTONOMOUS_MODE_ON:
				autonomousModeOn() ;
				break ;
			case CMD_AUTONOMOUS_MODE_OFF:
				autonomousModeOff() ;
				break ;
			default:
				lastError = "Invalid command ignored: " + command ;
				log.error(lastError) ;
			}
		}
		catch(XBeeException e) {
			lastError = "Error executing: " + stringifiedCommandName(command) + ": " + e.getMessage() ;
			log.error(lastError, e) ;
			throw e ;
		} finally {
			if(xbee != null && xbee.isConnected())
				xbee.close() ;
			xbee = null ;
		}
		return 0 ;
	}
		
	/**
	 * Receives data chunks from the publish method asynchronously on the EventDispatch thread.
	 * 
	 * Not used.
	 */
	@Override
	protected void process(List<Integer> chunks) {
//		System.out.println(chunks);
	}
	
	/**
	 * Executed on the EventDispatch thread once the doInBackground method is finished.
	 * 
	 * Updates the GUI's message bar based on the completion state of the operation, and 
	 * shuts down the XBee communication channel.
	 * 
	 * Also disables the cancel button in the GUI, as there is now nothing to cancel.
	 */
	@Override
	protected void done() {
		if (isCancelled()) {
			log.warn("Cancelled command: " + stringifiedCommandName(command)) ;
			if(lastError != null)
				gui.messageLabel.setText("Operation cancelled: " + lastError) ;
			else
				gui.messageLabel.setText("Operation cancelled (no errors)") ;
		} else if(lastError != null) {
			gui.messageLabel.setText(lastError) ;
		} else {
			gui.messageLabel.setText("Operation completed successfully") ;
			log.info("Completed command: " + stringifiedCommandName(command)) ;
		}
		
		/*
		 * Strictly speaking we shouldn't need to do this, as the doInBackground() method does this in it's finally
		 * block.
		 */
		if(xbee != null && xbee.isConnected())
			xbee.close();
		xbee = null ;
		/*
		 * Disable the cancel button because there is now nothing to cancel.
		 */
		gui.cancelButton.setEnabled(false) ;
	}
		
	/**
	 * Stop the 4WD platform.
	 * 
	 * @throws XBeeTimeoutException
	 * @throws XBeeException
	 */
	private void motorStop() throws XBeeTimeoutException, XBeeException {
		int[] payload = new int[] { this.CMD_MOTOR_STOP, 200 };
		XBeeAddress16 destination = new XBeeAddress16(XBEE_SHIELD_MY_MSB, XBEE_SHIELD_MY_LSB);
		sendCommand(destination, payload) ;
	}
	
	/**
	 * Move the 4WD platform forward.
	 * 
	 * @throws XBeeTimeoutException
	 * @throws XBeeException
	 */
	private void motorForward() throws XBeeTimeoutException, XBeeException {		
		int[] payload = new int[] { this.CMD_MOTOR_FORWARD, data };
		XBeeAddress16 destination = new XBeeAddress16(XBEE_SHIELD_MY_MSB, XBEE_SHIELD_MY_LSB);
		sendCommand(destination, payload) ;
	}
	
	/**
	 * Turn the 4WD platform backwards
	 * @throws XBeeTimeoutException
	 * @throws XBeeException
 	 */
	private void motorBackwards() throws XBeeTimeoutException, XBeeException {		
		int[] payload = new int[] { this.CMD_MOTOR_BACKWARDS, data };
		XBeeAddress16 destination = new XBeeAddress16(XBEE_SHIELD_MY_MSB, XBEE_SHIELD_MY_LSB);
		sendCommand(destination, payload) ;
	}
	
	/**
	 * Turn the 4WD platform left
	 * @throws XBeeTimeoutException
	 * @throws XBeeException
	 */
	private void motorLeft() throws XBeeTimeoutException, XBeeException {		
		int[] payload = new int[] { this.CMD_MOTOR_LEFT, data };
		XBeeAddress16 destination = new XBeeAddress16(XBEE_SHIELD_MY_MSB, XBEE_SHIELD_MY_LSB);
		sendCommand(destination, payload) ;
	}
	
	/**
	 * Turn the 4WD platform right
	 * @throws XBeeTimeoutException
	 * @throws XBeeException
	 */
	private void motorRight() throws XBeeTimeoutException, XBeeException {		
		int[] payload = new int[] { this.CMD_MOTOR_RIGHT, data };
		XBeeAddress16 destination = new XBeeAddress16(XBEE_SHIELD_MY_MSB, XBEE_SHIELD_MY_LSB);
		sendCommand(destination, payload) ;
	}
	
	/**
	 * Enable Autonomous Mode.
	 * 
	 * @throws XBeeTimeoutException
	 * @throws XBeeException
	 */
	private void autonomousModeOn() throws XBeeTimeoutException, XBeeException {		
		int[] payload = new int[] { this.CMD_AUTONOMOUS_MODE_ON, 0};
		XBeeAddress16 destination = new XBeeAddress16(XBEE_SHIELD_MY_MSB, XBEE_SHIELD_MY_LSB);
		sendCommand(destination, payload) ;
	}
	
	/**
	 * Disable Autonomous Mode.
	 * 
	 * @throws XBeeTimeoutException
	 * @throws XBeeException
	 */
	private void autonomousModeOff() throws XBeeTimeoutException, XBeeException {		
		int[] payload = new int[] { this.CMD_AUTONOMOUS_MODE_OFF, 0};
		XBeeAddress16 destination = new XBeeAddress16(XBEE_SHIELD_MY_MSB, XBEE_SHIELD_MY_LSB);
		sendCommand(destination, payload) ;
	}
	
	/**
	 * ping the remote XBee
	 * 
	 * @throws XBeeTimeoutException
	 * @throws XBeeException
	 */
	private void ping() throws XBeeTimeoutException, XBeeException {		
		int[] payload = new int[] { this.CMD_PING, 0 };
		XBeeAddress16 destination = new XBeeAddress16(XBEE_SHIELD_MY_MSB, XBEE_SHIELD_MY_LSB);
		sendCommand(destination, payload) ;
	}
	
	/**
	 * sends a command to the remote XBee.
	 * 
	 * @param destination the remote XBee's MY address
	 * @param payload an array of two 8 bit numbers - the first is the command, the second is the accompanying data.
	 * @throws XBeeTimeoutException if we timed out trying to communicate with the remote XBee
	 * @throws XBeeException if there was some other exception communicating with the remote XBee
	 */
	private void sendCommand(XBeeAddress16 destination, int[] payload) throws XBeeTimeoutException, XBeeException {
		TxRequest16 tx = new TxRequest16(destination, payload);
        log.info("Sending request to " + destination);
        TxStatusResponse status = (TxStatusResponse) xbee.sendSynchronous(tx);
        if (status.isSuccess()) {
                log.info("Sent payload to" + destination);
        } else {
                log.info("Error sending payload to" + destination);
        }
	}
	
	private String stringifiedCommandName(int command) {
		if(command > 0 && command < commandName.length)
			return commandName[command] ;
		else
			return "invalid (" + command + ")" ;
	}
}	
	
