package xerus.monstercat.tabs

import javafx.beans.InvalidationListener
import javafx.geometry.Insets
import javafx.scene.Node
import javafx.scene.control.TextField
import javafx.scene.control.TreeCell
import javafx.scene.control.TreeView
import javafx.scene.layout.VBox
import xerus.ktutil.helpers.RoughMap
import xerus.ktutil.helpers.Row
import xerus.ktutil.javafx.*
import xerus.ktutil.javafx.ui.FilterableTreeItem
import xerus.monstercat.Settings.GENRECOLORINTENSITY
import xerus.monstercat.Sheets
import xerus.monstercat.logger

val genreColors = RoughMap<String>()
val genreColor = { item: String? ->
	item?.let {
		"-fx-background-color: %s%02x".format(it, GENRECOLORINTENSITY())
	}
}

class TabGenres : FetchTab(Sheets.GENRESHEET, "A:H") {
	
	private val view = TreeView<String>()
	
	init {
		styleClass("tab-genres")
		val searchField = TextField()
		VBox.setMargin(searchField, Insets(0.0, 0.0, 6.0, 0.0))
		val root = FilterableTreeItem("")
		root.bindPredicate(searchField.textProperty()) { text, filter -> text.contains(filter, true) }
		view.isShowRoot = false
		data.addListener(InvalidationListener {
			view.root = root
			root.internalChildren.clear()
			var cur = root
			var curLevel = 0
			var style = ""
			for (list in data) {
				if (list.isEmpty())
					continue
				val row = Row(10, *list.toTypedArray())
				val nextLevel = row.indexOfFirst { it.isNotEmpty() }
				if (nextLevel < curLevel)
					repeat(curLevel - nextLevel) { cur = cur.parent as? FilterableTreeItem<String> ?: cur.also { logger.warning("$cur should have a parent!") } }
				
				/*if (hex != null) {
					if (nextLevel == 0) {
						style = row[hex]
						genreColors.put(row[0], style)
					}
					row[hex] = style
				}*/
				
				val new = FilterableTreeItem(row[nextLevel])
				cur.internalChildren.add(new)
				
				curLevel = nextLevel + 1
				cur = new
			}
		})
		
		/*view.setRowFactory {
			TreeTableRow<Row>().apply {
				if (GENRECOLORINTENSITY() > 0) {
					val hex = cols.find("Hex") ?: return@apply
					itemProperty().listen { style = genreColor(it?.get(hex)) }
				}
			}
		}*/
		
		view.setCellFactory {
			object : TreeCell<String>() {
				override fun updateItem(item: String, empty: Boolean) {
					super.updateItem(item, empty)
					text = item
					val treeItem = this.treeItem
					font = if (treeItem != null && treeItem.parent === root) {
						font.bold()
					} else {
						style = ""
						font.format()
					}
				}
			}
		}
		
		children.add(searchField)
		fill(view)
	}
	
	override fun setPlaceholder(n: Node) {}
	
	override fun refreshView() {
		view.refresh()
	}
	
}
