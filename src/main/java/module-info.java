module com.diozero.jfxapp {
	requires transitive javafx.controls;
	requires transitive diozero.core;
	requires diozero.provider.mock;
	// requires diozero.provider.firmata;
	// requires diozero.provider.remote;

	exports com.diozero.jfxapp;
}
