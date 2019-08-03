package xerus.monstercat.tabs

import javafx.geometry.Orientation
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.Label
import javafx.scene.control.Slider
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.scene.media.EqualizerBand
import xerus.ktutil.javafx.*
import xerus.ktutil.javafx.properties.listen
import xerus.monstercat.Settings
import xerus.monstercat.Settings.EQUALIZERBANDS
import xerus.monstercat.api.Player

class TabSound : VTab() {
	var hint: Label? = null
	val eqBox = HBox()
	var eqModel: MutableList<Double> = mutableListOf()
	
	init {
		val resetButton = createButton("Reset") {
			EQUALIZERBANDS.clear()
			updateEQBox()
		}
		addRow(CheckBox("Enable Equalizer").bind(Settings.ENABLEEQUALIZER), resetButton)
		Player.activePlayer.listen { onFx { updateEQBox() } }
		
		hint = Label("Play a song to display the controls").run(::add)
		add(eqBox)
	}
	
	private fun updateEQBox() {
		eqBox.children.clear()
		Player.player?.audioEqualizer?.let { eq ->
			// Remove hint once equalizer has been initialized
			hint?.let(children::remove)
			hint = null
			
			// Sync view with equalizer model
			eqModel = if(EQUALIZERBANDS.get().isNotEmpty()) EQUALIZERBANDS.all.map { it.toDouble() }.toMutableList() else MutableList(eq.bands.size) { 0.0 }
			eq.enabledProperty().bind(Settings.ENABLEEQUALIZER)
			for ((i, band) in eq.bands.withIndex()) {
				eqBox.children.add(createEQBandView(band, eqModel[i]) {
					eqModel[i] = it
					EQUALIZERBANDS.putMulti(*eqModel.map { it.toString() }.toTypedArray())
				})
			}
		} ?: Settings.ENABLEEQUALIZER.unbind()
	}
	
	private fun createEQBandView(band: EqualizerBand, value: Double, listener: (Double) -> Unit): VBox {
		return VBox().apply {
			children.addAll(
				Slider(EqualizerBand.MIN_GAIN, EqualizerBand.MAX_GAIN, 1.0).apply {
					orientation = Orientation.VERTICAL
					band.gainProperty().bind(valueProperty())
					valueProperty().set(value)
					valueProperty().listen { listener(it as Double) }
				}.scrollable(),
				Label(band.centerFrequency.toString())
			)
		}
	}
}