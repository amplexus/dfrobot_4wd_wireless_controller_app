package org.amplexus.dfrobot.app;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.LayoutManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JToggleButton;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

/**
 * An application to control a DFRobot 4WD platform controller via an XBee explorer.
 * 
 * 
 * Icons c/- http://www.iconspedia.com/pack/crystal-clear-actions-1303/
 * 
 * @author craig
 * 
 */
public class DFRobot4WDPlatformController implements KeyListener {

	/*
	 * Defaults
	 */
	public static final String	DEFAULT_USBPORT		= "/dev/ttyUSB0" ;
	public static final int		DEFAULT_SPEED		= 200 ;
	public static final int		DEFAULT_BAUD_RATE	= 9600 ;

    private final static Logger log = Logger.getLogger(XBeeCommunicatorTask.class);

    /*
     *  The SwingWorker thread that communicates with the robot wirelessly via XBee devices
     */
	XBeeCommunicatorTask task = null ;				

	/*
	 * User interface widgets
	 */
	protected JToggleButton leftButton ; 			// Left arrow - moves the robot to the left
	protected JToggleButton rightButton ;			// Right arrow - moves the robot to the right
	protected JToggleButton upButton ;				// Up arrow - moves the robot forward
	protected JToggleButton downButton ;			// Down arrow - moves the robot backwards
	protected JToggleButton stopButton ;			// Stop button - puts the robot in a stationary position
	protected JLabel messageLabel ;					// Message bar - displays status messages at the bottom of the window
	protected JButton aboutButton ;					// About button - shows a dialog box
	protected JButton cancelButton ;				// Cancel button - cancels the currently executing XbeeCommunicatorTask
	protected JComboBox baudRateComboBox ;			// Baud rate - choose the speed at which we talk to the robot
	protected JComboBox usbPortComboBox ;			// USB port - choose the USB port through which we talk to the robot
	protected JToggleButton autonomousModeButton ;	// Autonomous mode - disabled means we control manually
													// Autonomous mode - enabled means it navigates its own way around
	protected JLabel speedLabel ;					// Label for the speed slider
	protected JSlider speedSlider ;					// Speed - choose the speed that the robot will move at

	/*
	 * Current operating parameters
	 */
	String usbPort = DEFAULT_USBPORT ;				// The current USB port we talk to the robot through
	int speed = DEFAULT_SPEED ;						// The current speed that the robot should travel at when moving
	int baudRate = DEFAULT_BAUD_RATE ;				// The current baud rate that we talk to the robot at
	
	/**
	 * The main method.
	 * 
	 * Initialises the logger and displays the user interface.
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
        PropertyConfigurator.configure("log4j.properties");

		DFRobot4WDPlatformController demo = new DFRobot4WDPlatformController();
		demo.displayGUI();
	}

	/**
	 * Displays the GUI.
	 * 
	 * Initialises the various user interface components, prepares the layout and displays the user interface.
	 */
	private void displayGUI() {

		/*
		 * Message bar
		 */
		messageLabel = new JLabel("Use arrow keys to move, the period (.) key to stop") ;
		
		/*
		 * Header
		 */
		aboutButton = new JButton("About") ;
		aboutButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				JOptionPane.showMessageDialog((Component) e.getSource(), "DFRobot 4WD Platform Wireless Controller v1.0") ;
			}
		});

		cancelButton = new JButton("Stop") ;
		cancelButton.setEnabled(false) ;
		cancelButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if(!task.isDone())
					task.cancel(true) ;
			}
		});

		autonomousModeButton = new JToggleButton("Auto", false) ;
		autonomousModeButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				toggleAutonomousMode() ;
			}
		});
		Integer[] baudRates = {2400, 4800, 9600, 19200, 38400, 76800, 153600 } ;
		baudRateComboBox = new JComboBox(baudRates) ;
		baudRateComboBox.setSelectedIndex(2) ;
		baudRateComboBox.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				baudRate = (Integer) baudRateComboBox.getSelectedItem() ;
			}
		});

		String[] usbPorts = enumerateUsbPorts() ;
		usbPortComboBox = new JComboBox(usbPorts) ;
		usbPortComboBox.setSelectedIndex(0) ;
		usbPortComboBox.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				usbPort = (String) usbPortComboBox.getSelectedItem() ;
			}
		});
		
		
		speedLabel = new JLabel("Speed: ") ;
		
		speedSlider = new JSlider(JSlider.HORIZONTAL, 0, 255, 200) ;
		speedSlider.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				speed = speedSlider.getValue() ;
				
				// FIXME: slider state changes sends updates too quickly to XBee causing USB port resource contention
				
//				/*
//				 * If we've selected a speed of zero, then that's the same as not moving.
//				 */
//				if(speed == 0) {
//					stopButton.setSelected(true) ;
//					stopMoving() ;
//				}
//				
//				/*
//				 * All we're doing here is ensuring that if the robot is currently moving, we simply 
//				 * re-send the last request, but with the newly selected speed.
//				 */
//				if(upButton.isSelected())
//					moveForward() ;
//				else if(downButton.isSelected())
//					moveBackwards() ;
//				else if(leftButton.isSelected())
//					moveLeft() ;
//				else if(rightButton.isSelected())
//					moveRight() ;
			}
		});
		speedSlider.setMajorTickSpacing(50) ;
		speedSlider.setMinorTickSpacing(10) ;
		speedSlider.setPaintTicks(true) ;
		speedSlider.setPaintLabels(true) ;

		/*
		 * Load some images for the buttons we will display on the screen.
		 */
		ImageIcon leftIcon = createImageIcon("/leftarrow-128.png", "Left") ;
		ImageIcon rightIcon = createImageIcon("/rightarrow-128.png", "Right") ;
		ImageIcon upIcon = createImageIcon("/uparrow-128.png", "Up") ;
		ImageIcon downIcon = createImageIcon("/downarrow-128.png", "Down") ;
		ImageIcon stopIcon = createImageIcon("/stop-128.png", "Stop") ;
		
		ImageIcon selectedLeftIcon = createImageIcon("/selected-leftarrow-128.png", "Left") ;
		ImageIcon selectedRightIcon = createImageIcon("/selected-rightarrow-128.png", "Right") ;
		ImageIcon selectedUpIcon = createImageIcon("/selected-uparrow-128.png", "Up") ;
		ImageIcon selectedDownIcon = createImageIcon("/selected-downarrow-128.png", "Down") ;
		ImageIcon selectedStopIcon = createImageIcon("/selected-stop-128.png", "Stop") ;

		/*
		 * This one not used as a button, it's just used for padding so the arrows are all aligned properly.
		 */
		ImageIcon spacerIcon = createImageIcon("/spacer-128.png", "Left") ;
		JLabel spacerLabel1 = new JLabel(spacerIcon) ;
		JLabel spacerLabel2 = new JLabel(spacerIcon) ;
		JLabel spacerLabel3 = new JLabel(spacerIcon) ;
		JLabel spacerLabel4 = new JLabel(spacerIcon) ;
		
		/*
		 * JToggleButtons will toggle between selected and unselected state with each button press.
		 */
		leftButton = new JToggleButton(leftIcon) ;
		leftButton.setPressedIcon(selectedLeftIcon) ;
		leftButton.addKeyListener(this) ;
		leftButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				moveLeft() ;
			}
		});

		rightButton = new JToggleButton(rightIcon) ;
		rightButton.setPressedIcon(selectedRightIcon) ;
		rightButton.addKeyListener(this) ;
		rightButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				moveRight() ;
			}
		});
		
		upButton = new JToggleButton(upIcon) ;
		upButton.setPressedIcon(selectedUpIcon) ;
		upButton.addKeyListener(this) ;
		upButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				moveForward() ;
			}
		});

		downButton = new JToggleButton(downIcon) ;
		downButton.setPressedIcon(selectedDownIcon) ;
		downButton.addKeyListener(this) ;
		downButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				moveBackwards() ;
			}
		});

		stopButton = new JToggleButton(stopIcon) ;
		stopButton.setPressedIcon(selectedStopIcon) ;
		stopButton.addKeyListener(this) ;
		stopButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				stopMoving() ;
			}
		});

		/*
		 * A ButtonGroup makes sure only one of the buttons can be selected at any time. Basically radiobutton behaviour.
		 */
		ButtonGroup buttonGroup = new ButtonGroup() ;
		buttonGroup.add(leftButton) ;
		buttonGroup.add(rightButton) ;
		buttonGroup.add(upButton) ;
		buttonGroup.add(downButton) ;
		buttonGroup.add(stopButton) ;

		/*
		 * Default to stopped state
		 */
		stopButton.setSelected(true) ;
		
		/*
		 * Layout our GUI
		 */
		
		JPanel headerPanel1 = new JPanel();
		LayoutManager headerPanel1BoxLayout = new BoxLayout(headerPanel1, BoxLayout.LINE_AXIS) ;
		headerPanel1.setLayout(headerPanel1BoxLayout) ;
		headerPanel1.add(aboutButton) ;
		headerPanel1.add(autonomousModeButton) ;
		headerPanel1.add(baudRateComboBox) ;
		headerPanel1.add(usbPortComboBox) ;
		
		JPanel headerPanel2 = new JPanel();
		LayoutManager headerPanel2BoxLayout = new BoxLayout(headerPanel2, BoxLayout.LINE_AXIS) ;
		headerPanel2.setLayout(headerPanel2BoxLayout) ;
//		headerPanel2.add(speedLabel) ;
		headerPanel2.add(speedSlider) ;
		headerPanel2.add(cancelButton) ;

		JPanel headerPanel = new JPanel();
		LayoutManager headerPanelBoxLayout = new BoxLayout(headerPanel, BoxLayout.PAGE_AXIS) ;
		headerPanel.setLayout(headerPanelBoxLayout) ;
		headerPanel.add(headerPanel1) ;
		headerPanel.add(headerPanel2) ;

		JPanel leftPanel = new JPanel() ;
		LayoutManager leftPanelBoxLayout = new BoxLayout(leftPanel, BoxLayout.PAGE_AXIS) ;
		leftPanel.setLayout(leftPanelBoxLayout) ;
		leftPanel.add(spacerLabel1) ;
		leftPanel.add(leftButton) ;
		leftPanel.add(spacerLabel2) ;
		
		JPanel centerPanel = new JPanel() ;
		LayoutManager centerPanelBoxLayout = new BoxLayout(centerPanel, BoxLayout.PAGE_AXIS) ;
		centerPanel.setLayout(centerPanelBoxLayout) ;
		centerPanel.add(upButton) ;
		centerPanel.add(stopButton) ;
		centerPanel.add(downButton) ;
		
		JPanel rightPanel = new JPanel() ;
		LayoutManager rightPanelBoxLayout = new BoxLayout(rightPanel, BoxLayout.PAGE_AXIS) ;
		rightPanel.setLayout(rightPanelBoxLayout) ;
		rightPanel.add(spacerLabel3) ;
		rightPanel.add(rightButton) ;
		rightPanel.add(spacerLabel4) ;
		
		JPanel contentPanel = new JPanel();
		LayoutManager borderLayout = new BorderLayout() ;
		
		contentPanel.setLayout(borderLayout);
		contentPanel.add(headerPanel, BorderLayout.PAGE_START) ;
		contentPanel.add(messageLabel, BorderLayout.PAGE_END) ;
		contentPanel.add(leftPanel, BorderLayout.LINE_START) ;
		contentPanel.add(centerPanel, BorderLayout.CENTER) ;
		contentPanel.add(rightPanel, BorderLayout.LINE_END) ;

		JFrame frame = new JFrame();
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setContentPane(contentPanel);
		frame.pack();
		frame.setResizable(false) ;
		frame.setLocationRelativeTo(null) ;
		frame.setTitle("Mobile 4WD Platform Controller v1.0 (c) Craig Jackson 2012") ;
		frame.setVisible(true);
	}

	/**
	 * 
	 * @param path the path to the icon
	 * @param description the text that will be displayed with the icon
	 * @return
	 */
	private ImageIcon createImageIcon(String path, String description) {
		java.net.URL imgURL = getClass().getResource(path);
		if (imgURL != null) {
			return new ImageIcon(imgURL, description);
		} else {
			log.error("Couldn't find file: " + path);
			return null;
		}
	}

	/** 
	 * Handle the key typed event from the text field.
	 * 
	 * We don't use this - mainly because it fires repeatedly if the key is held down, and we don't want that behaviour.
	 */
	@Override
	public void keyTyped(KeyEvent e) {
	}

	/** 
	 * Handle the key-pressed event from the text field.
	 * 
	 * Not interested in the key press.
	 */
	@Override
	public void keyPressed(KeyEvent e) {
	}

	/** 
	 * Handle the key-released event from the text field.
	 * 
	 * We only talk to the robot when the key has been released, so handle the keyboard commands here.
	 */
	@Override
	public void keyReleased(KeyEvent e) {
		int keyCode = e.getKeyCode() ;
		switch(keyCode) {
		case KeyEvent.VK_DOWN:
			keyDown() ;
			break ;
		case KeyEvent.VK_UP:
			keyUp() ;
			break ;
		case KeyEvent.VK_LEFT:
			keyLeft() ;
			break ;
		case KeyEvent.VK_RIGHT:
			keyRight() ;
			break ;
		case KeyEvent.VK_PERIOD :
			keyStop() ;
			break ;
		}
	}

	/**
	 * The left arrow key was pressed.
	 * 
	 * Make the left arrow button selected and move to the left.
	 */
	private void keyLeft() {
		leftButton.setSelected(true) ;
		moveLeft() ;
	}
	
	/**
	 * The right arrow key was pressed.
	 * 
	 * Make the right arrow button selected and move to the right.
	 */
	private void keyRight() {
		rightButton.setSelected(true) ;
		moveRight() ;
	}
	
	/**
	 * The up arrow key was pressed.
	 * 
	 * Make the up arrow button selected and move forward.
	 */
	private void keyUp() {
		upButton.setSelected(true) ;
		moveForward() ;
	}
	
	/**
	 * The down arrow key was pressed.
	 * 
	 * Make the down arrow button selected and move backwards.
	 */
	private void keyDown() {
		downButton.setSelected(true) ;
		moveBackwards() ;
	}
	
	/**
	 * The period (.) key was pressed.
	 * 
	 * Make the stop button selected and stop moving.
	 */
	private void keyStop() {
		stopButton.setSelected(true) ;
		stopMoving() ;
	}

	/**
	 * Make the robot move to the left.
	 */
	private void moveLeft() {
		killPreviousCommand() ;
		task = new XBeeCommunicatorTask(XBeeCommunicatorTask.CMD_MOTOR_LEFT, speed, usbPort, baudRate, this);
		cancelButton.setEnabled(true) ;
		task.execute() ;
		log.info("Moving left") ;
	}
	
	/**
	 * Make the robot move to the right.
	 */
	private void moveRight() {
		killPreviousCommand() ;
		task = new XBeeCommunicatorTask(XBeeCommunicatorTask.CMD_MOTOR_RIGHT, speed, usbPort, baudRate, this);
		cancelButton.setEnabled(true) ;
		task.execute() ;
		log.info("Moving right") ;
	}
	
	/**
	 * Make the robot move forward.
	 */
	private void moveForward() {
		killPreviousCommand() ;
		log.info("Moving forward") ;
		task = new XBeeCommunicatorTask(XBeeCommunicatorTask.CMD_MOTOR_FORWARD, speed, usbPort, baudRate, this);
		cancelButton.setEnabled(true) ;
		task.execute() ;
	}
	
	/**
	 * Make the robot move backwards.
	 */
	private void moveBackwards() {
		killPreviousCommand() ;
		log.info("Moving backwards") ;
		task = new XBeeCommunicatorTask(XBeeCommunicatorTask.CMD_MOTOR_BACKWARDS, speed, usbPort, baudRate, this);
		cancelButton.setEnabled(true) ;
		task.execute() ;
	}
	
	/**
	 * Make the robot stop moving.
	 */
	private void stopMoving() {
		killPreviousCommand() ;
		log.info("Stop moving") ;
		task = new XBeeCommunicatorTask(XBeeCommunicatorTask.CMD_MOTOR_STOP, 0, usbPort, baudRate, this);
		cancelButton.setEnabled(true) ;
		task.execute() ;
	}
	
	private void toggleAutonomousMode() {
		killPreviousCommand() ;
		log.info("Toggling autonomous mode") ;
		if(autonomousModeButton.isSelected()) {
			task = new XBeeCommunicatorTask(XBeeCommunicatorTask.CMD_AUTONOMOUS_MODE_ON, 0, usbPort, baudRate, this);
			upButton.setEnabled(false) ;
			downButton.setEnabled(false) ;
			leftButton.setEnabled(false) ;
			rightButton.setEnabled(false) ;
			stopButton.setEnabled(false) ;
		} else {
			task = new XBeeCommunicatorTask(XBeeCommunicatorTask.CMD_AUTONOMOUS_MODE_OFF, 0, usbPort, baudRate, this);
			upButton.setEnabled(true) ;
			downButton.setEnabled(true) ;
			leftButton.setEnabled(true) ;
			rightButton.setEnabled(true) ;
			stopButton.setEnabled(true) ;
		}
		cancelButton.setEnabled(true) ;
		task.execute() ;
	}
	/**
	 * Detects the candidate USB ports for communicating with the robot via an attached XBee explorer.
	 * 
	 * @return a list of candidate USB ports.
	 */
	private String[] enumerateUsbPorts() {
		String[] usbPorts = {"/dev/ttyUSB0", "/dev/ttyUSB1", "/dev/ttyUSB2", "/dev/ttyUSB3", "/dev/ttyUSB4", "/dev/ttyUSB5", } ; 
		return usbPorts ;
	}

	/**
	 * Kill the previous command if it is still running.
	 */
	private void killPreviousCommand() {
		if(task != null && task.isDone() == false) {
			task.cancel(true) ;
		}
	}
}