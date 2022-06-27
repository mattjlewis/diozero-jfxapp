package com.diozero.jfxapp;

import java.util.HashMap;
import java.util.Map;

import com.diozero.api.DeviceMode;
import com.diozero.api.GpioEventTrigger;
import com.diozero.api.GpioPullUpDown;
import com.diozero.api.PinInfo;
import com.diozero.api.RuntimeIOException;
import com.diozero.api.ServoTrim;
import com.diozero.internal.spi.InternalDeviceInterface;
import com.diozero.internal.spi.NativeDeviceFactoryInterface;
import com.diozero.sbc.DeviceFactoryHelper;
import com.diozero.util.Diozero;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * JavaFX App
 */
public class App extends Application {
	private NativeDeviceFactoryInterface deviceFactory;
	private Map<Integer, InternalDeviceInterface> devices;
	private Map<Integer, Pane> valuePanes;

	@Override
	public void start(Stage stage) {
		deviceFactory = DeviceFactoryHelper.getNativeDeviceFactory();
		var board_info = deviceFactory.getBoardInfo();
		devices = new HashMap<>();
		valuePanes = new HashMap<>();

		var header_pane = new GridPane();
		header_pane.setHgap(5);
		header_pane.setVgap(5);

		int row = 0;
		header_pane.add(new Label("diozero:"), 0, row);
		header_pane.add(new Label(Diozero.getVersion()), 1, row++);
		header_pane.add(new Label("Make:"), 0, row);
		header_pane.add(new Label(board_info.getMake()), 1, row++);
		header_pane.add(new Label("Model:"), 0, row);
		header_pane.add(new Label(board_info.getModel()), 1, row++);
		header_pane.add(new Label("Name:"), 0, row);
		header_pane.add(new Label(board_info.getName()), 1, row++);

		var pins_pane = new GridPane();
		pins_pane.setHgap(5);
		pins_pane.setVgap(5);

		for (var header_entry : board_info.getHeaders().entrySet()) {
			pins_pane.add(new Label("Header:"), 0, row);
			pins_pane.add(new Label(header_entry.getKey()), 1, row++, 3, 1);

			int col = 0;
			pins_pane.add(new Label("Pin"), col++, row);
			pins_pane.add(new Label("GPIO"), col++, row);
			pins_pane.add(new Label("Name"), col++, row);
			pins_pane.add(new Label("Modes"), col++, row);
			pins_pane.add(new Label("Value"), col++, row);
			row++;

			for (var pin_info : header_entry.getValue().values()) {
				col = 0;

				pins_pane.add(new Label(Integer.toString(pin_info.getPhysicalPin())), col++, row);
				pins_pane.add(
						new Label(pin_info.getDeviceNumber() == -1 ? "" : Integer.toString(pin_info.getDeviceNumber())),
						col++, row);
				Label name_label = new Label(pin_info.getName());
				name_label.getStyleClass().add("name-" + pin_info.getName().toLowerCase());
				pins_pane.add(name_label, col++, row);

				if (pin_info.getModes().isEmpty()) {
					pins_pane.add(new Label(""), col++, row);
				} else {
					DeviceMode current_mode = deviceFactory.getGpioMode(pin_info.getDeviceNumber());

					final ChoiceBox<DeviceMode> cb = new ChoiceBox<>();
					cb.getItems().addAll(pin_info.getModes());
					cb.setValue(current_mode);
					cb.setOnAction(event -> pinModeChangedAction(pin_info, event));
					pins_pane.add(cb, col++, row);

					// Create a placeholder pane to hold the device-specific controls
					var value_pane = new HBox();
					valuePanes.put(Integer.valueOf(pin_info.getDeviceNumber()), value_pane);
					pins_pane.add(value_pane, col++, row);

					provisionDevice(pin_info, current_mode);
				}

				row++;
			}
		}

		var scroll_pane = new ScrollPane(new VBox(header_pane, new Separator(Orientation.HORIZONTAL), pins_pane));

		var scene = new Scene(scroll_pane, 640, 480);
		scene.getStylesheets().add("diozero.css");

		stage.setScene(scene);
		stage.show();
	}

	@SuppressWarnings("resource")
	private void provisionDevice(PinInfo pinInfo, DeviceMode mode) {
		// Locate the existing device (if present)
		final var device = devices.get(Integer.valueOf(pinInfo.getDeviceNumber()));
		if (device != null) {
			// Already provisioned in the same mode?
			// TODO add device.getMode...
			device.close();
		}

		final var value_pane = valuePanes.get(Integer.valueOf(pinInfo.getDeviceNumber()));
		value_pane.getChildren().clear();

		try {
			switch (mode) {
			case DIGITAL_INPUT:
				provisionDigitalInput(pinInfo, value_pane);
				break;
			case DIGITAL_OUTPUT:
				provisionDigitalOutput(pinInfo, value_pane);
				break;
			case PWM_OUTPUT:
				provisionPwmOutput(pinInfo, value_pane);
				break;
			case SERVO:
				provisionServo(pinInfo, value_pane);
				break;
			case ANALOG_INPUT:
				provisionAnalogInput(pinInfo, value_pane);
				break;
			case UNKNOWN:
				// Ignore
				break;
			default:
				System.out.println("Unhandled pin device mode " + mode);
			}
		} catch (RuntimeIOException e) {
			System.out.println("Error provisioning pin " + pinInfo + ": " + e);
		}
	}

	@SuppressWarnings("resource")
	private void provisionDigitalInput(PinInfo pinInfo, Pane valuePane) {
		final var digital_input = deviceFactory.provisionDigitalInputDevice(pinInfo, GpioPullUpDown.NONE,
				GpioEventTrigger.BOTH);
		devices.put(Integer.valueOf(pinInfo.getDeviceNumber()), digital_input);

		final var watch = new ToggleButton("Watch");
		valuePane.getChildren().add(watch);

		final var radio_button = new RadioButton();
		radio_button.setDisable(true);
		radio_button.getStyleClass().add("readonly-disabled");
		valuePane.getChildren().add(radio_button);

		watch.selectedProperty().addListener((observable, oldValue, newValue) -> {
			if (newValue.booleanValue()) {
				boolean is_set = digital_input.getValue();
				radio_button.selectedProperty().set(is_set);
				radio_button.getStyleClass().remove("readonly-disabled");
				radio_button.getStyleClass().add("readonly-enabled");
				digital_input.setListener(input_event -> Platform.runLater(() -> {
					radio_button.selectedProperty().set(input_event.isActive());
				}));
			} else {
				digital_input.removeListener();
				radio_button.selectedProperty().set(false);
				radio_button.getStyleClass().remove("readonly-enabled");
				radio_button.getStyleClass().add("readonly-disabled");
			}
		});
	}

	@SuppressWarnings("resource")
	private void provisionDigitalOutput(PinInfo pinInfo, Pane valuePane) {
		final var digital_output = deviceFactory.provisionDigitalOutputDevice(pinInfo, false);
		devices.put(Integer.valueOf(pinInfo.getDeviceNumber()), digital_output);

		final var on = new ToggleButton("On");
		on.getStyleClass().add("output");
		on.selectedProperty()
				.addListener((observable, oldValue, newValue) -> digital_output.setValue(newValue.booleanValue()));
		valuePane.getChildren().add(on);
	}

	@SuppressWarnings("resource")
	private void provisionPwmOutput(PinInfo pinInfo, Pane valuePane) {
		final var pwm_output = deviceFactory.provisionPwmOutputDevice(pinInfo, 50, 0);
		devices.put(Integer.valueOf(pinInfo.getDeviceNumber()), pwm_output);

		final var slider = new Slider(0, 100, 100 * pwm_output.getValue());
		slider.setShowTickMarks(true);
		slider.setShowTickLabels(true);
		slider.valueProperty()
				.addListener((observable, oldValue, newValue) -> pwm_output.setValue(newValue.floatValue() / 100f));
		valuePane.getChildren().add(slider);
	}

	@SuppressWarnings("resource")
	private void provisionServo(PinInfo pinInfo, Pane valuePane) {
		final var trim = ServoTrim.DEFAULT;
		final var servo = deviceFactory.provisionServoDevice(pinInfo, 50, trim.getMinPulseWidthUs(),
				trim.getMaxPulseWidthUs(), trim.getMidPulseWidthUs());
		devices.put(Integer.valueOf(pinInfo.getDeviceNumber()), servo);

		final var slider = new Slider(trim.getMinPulseWidthUs(), trim.getMaxPulseWidthUs(), servo.getPulseWidthUs());
		slider.setShowTickMarks(true);
		slider.setShowTickLabels(true);
		slider.valueProperty()
				.addListener((observable, oldValue, newValue) -> servo.setPulseWidthUs(newValue.intValue()));
		valuePane.getChildren().add(slider);
	}

	@SuppressWarnings("resource")
	private void provisionAnalogInput(PinInfo pinInfo, Pane valuePane) {
		final var analog_input = deviceFactory.provisionAnalogInputDevice(pinInfo);
		devices.put(Integer.valueOf(pinInfo.getDeviceNumber()), analog_input);

		final var watch = new ToggleButton("Watch");
		valuePane.getChildren().add(watch);

		final var slider = new Slider(0, 1, 0);
		slider.setDisable(true);
		slider.getStyleClass().add("readonly-disabled");
		valuePane.getChildren().add(slider);

		watch.selectedProperty().addListener((observable, oldValue, newValue) -> {
			if (newValue.booleanValue()) {
				float value = analog_input.getValue();
				slider.setValue(value);
				slider.getStyleClass().remove("readonly-disabled");
				slider.getStyleClass().add("readonly-enabled");
				analog_input.setListener(input_event -> Platform.runLater(() -> {
					slider.setValue(input_event.getUnscaledValue());
				}));
			} else {
				analog_input.removeListener();
				slider.getStyleClass().remove("readonly-enabled");
				slider.getStyleClass().add("readonly-disabled");
			}
		});
	}

	void pinModeChangedAction(PinInfo pinInfo, ActionEvent event) {
		System.out.println("pinModeChangedAction(" + pinInfo + ", " + event.getEventType());

		@SuppressWarnings("unchecked")
		final ChoiceBox<DeviceMode> cb = (ChoiceBox<DeviceMode>) event.getSource();
		provisionDevice(pinInfo, cb.getSelectionModel().getSelectedItem());
	}

	@Override
	public void stop() {
		try {
			if (devices != null) {
				devices.values().forEach(InternalDeviceInterface::close);
				devices.clear();
			}
			Diozero.shutdown();
		} catch (Throwable t) {
			System.out.println("Error: " + t);
			t.printStackTrace(System.out);
		}
	}

	public static void main(String[] args) {
		launch();
	}

}