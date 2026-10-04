"""Main window: project and line management, shot navigation, layout and interpretation controls."""

import math
from functools import partial
from pathlib import Path

from PySide6.QtCore import QSignalBlocker, Qt, Signal
from PySide6.QtGui import QAction, QColor, QKeySequence, QShortcut
from PySide6.QtWidgets import (QColorDialog, QDoubleSpinBox, QFileDialog, QFormLayout, QGridLayout,
                               QGroupBox, QHBoxLayout, QLabel, QMainWindow, QMessageBox, QPushButton,
                               QRadioButton, QScrollArea, QSpinBox, QSplitter, QVBoxLayout, QWidget)

from refrac.dialogs import NewLineDialog, NewProjectDialog
from refrac.interpretation import average_velocity, compute_interpretation, velocity
from refrac.layer_chart import LayerChart
from refrac.pick_chart import (DRAWER_NAMES, ERASER, HEAD_NAMES, ZOOM, PickChart, PickStyle,
                               wave_style)
from refrac.storage import Line, Project
from refrac.structs import UNDEFINED, Point, Shot, is_point_defined

# Interpretation rows: wave title, then its left and right drawers
WAVES = (("Direct", "directL", "directR"),
         ("Refracted 1", "head1L", "head1R"),
         ("Refracted 2", "head2L", "head2R"),
         ("Refracted 3", "head3L", "head3R"))


class MainWindow(QMainWindow):

    def __init__(self):
        super().__init__()
        self.resize(1280, 860)

        self.project: Project | None = None
        self.line: Line | None = None
        # Charts are built anew for each line
        self.pick_chart: PickChart | None = None
        self.layer_chart: LayerChart | None = None

        self.main_shot_index = 0
        self.amount_loaded_shots = 1
        self.main_shot: Shot | None = None
        self.loaded_shots: list[Shot] = []
        self._main_position = 0  # index of the main shot in loaded_shots
        # Draw points as last saved or loaded, to detect unsaved changes
        self.last_saved_draw_points: list[Point] | None = None

        # Tools of the pick chart, at most one checked: zoom, eraser and the eight line drawers
        self._tool_buttons: dict[str, QAction | QRadioButton] = {}

        self._build_menus()
        self._build_toolbar()
        self._charts = QSplitter(Qt.Orientation.Vertical)
        splitter = QSplitter(Qt.Orientation.Horizontal)
        splitter.addWidget(self._build_sidebar())
        splitter.addWidget(self._charts)
        splitter.setStretchFactor(1, 1)
        splitter.setSizes([300, 980])
        self.setCentralWidget(splitter)

        self._zoom_hint = QLabel("Zoomed in: press Esc to reset the zoom level")
        self._zoom_hint.hide()
        self.statusBar().addPermanentWidget(self._zoom_hint)

        for tool, button in self._tool_buttons.items():
            button.toggled.connect(partial(self._on_tool_toggled, tool))

        # Text and spin fields keep these keys while they have focus
        QShortcut(QKeySequence(Qt.Key.Key_F), self).activated.connect(self._on_fit_shortcut)
        QShortcut(QKeySequence(Qt.Key.Key_Escape), self).activated.connect(self._on_escape)

        self._on_layout_changed()
        self._on_drawings_changed()
        self._update_state()

    # UI construction

    def _add_action(self, container, text: str, slot=None, shortcut=None,
                    checkable: bool = False) -> QAction:
        action = QAction(text, self)
        action.setCheckable(checkable)
        if shortcut is not None:
            action.setShortcut(shortcut)
        if slot is not None:
            action.triggered.connect(slot)
        container.addAction(action)
        return action

    def _build_menus(self) -> None:
        menu_bar = self.menuBar()

        file_menu = menu_bar.addMenu("&File")
        self._add_action(file_menu, "&Quit", self.close, QKeySequence.StandardKey.Quit)

        project_menu = menu_bar.addMenu("&Project")
        self._add_action(project_menu, "&New Project...", self._on_new_project)
        self._add_action(project_menu, "&Open Project...", self._on_open_project)
        project_menu.addSeparator()
        self._add_action(project_menu, "&Close", lambda: self.set_project(None))

        self._line_menu = menu_bar.addMenu("&Line")
        self._add_action(self._line_menu, "&New Line...", self._on_new_line)
        self._add_action(self._line_menu, "&Open Line...", self._on_open_line)
        self._line_menu.addSeparator()
        self._add_action(self._line_menu, "&Close", lambda: self.set_line(None))

    def _build_toolbar(self) -> None:
        toolbar = self._toolbar = self.addToolBar("Shot")
        toolbar.setMovable(False)

        self._add_action(toolbar, "Save", self.save_plot, QKeySequence.StandardKey.Save)
        toolbar.addSeparator()
        self._add_action(toolbar, "<", self._on_previous_shot)
        self._add_action(toolbar, ">", self._on_next_shot)
        toolbar.addSeparator()

        self._seq_num_label = QLabel()
        self._source_station_label = QLabel()
        self._coordinates_label = QLabel()
        for title, label in (("Seq. number", self._seq_num_label),
                             ("Source station", self._source_station_label),
                             ("Coordinates", self._coordinates_label)):
            toolbar.addWidget(_titled(title, label))
            toolbar.addSeparator()

        toolbar.addWidget(QLabel(" Seq. number "))
        self._go_spin = QSpinBox()
        self._go_spin.setMinimum(1)
        self._go_spin.lineEdit().returnPressed.connect(self._on_go_to_shot)
        toolbar.addWidget(self._go_spin)
        self._add_action(toolbar, "Go", self._on_go_to_shot)
        toolbar.addSeparator()

        toolbar.addWidget(QLabel(" Shots loaded "))
        self._shots_loaded_spin = QSpinBox()
        self._shots_loaded_spin.setRange(1, 999)
        self._shots_loaded_spin.lineEdit().returnPressed.connect(self._on_apply_shots_loaded)
        toolbar.addWidget(self._shots_loaded_spin)
        self._add_action(toolbar, "Apply", self._on_apply_shots_loaded)
        toolbar.addSeparator()

        self._tool_buttons[ZOOM] = self._add_action(toolbar, "Zoom", checkable=True)
        self._tool_buttons[ERASER] = self._add_action(toolbar, "Erase picks", checkable=True)

    def _build_sidebar(self) -> QWidget:
        content = QWidget()
        layout = QVBoxLayout(content)
        layout.addWidget(self._build_layout_box())
        layout.addWidget(self._build_interpretation_box())
        layout.addWidget(self._build_velocities_box())
        layout.addStretch()
        scroll = QScrollArea()
        scroll.setWidget(content)
        scroll.setWidgetResizable(True)
        scroll.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        return scroll

    def _build_layout_box(self) -> QGroupBox:
        box = self._layout_box = QGroupBox("Layout")
        form = QFormLayout(box)

        self._symbols_toggle = QPushButton("Symbols")
        self._symbols_toggle.setCheckable(True)
        self._symbols_toggle.setChecked(True)
        self._line_toggle = QPushButton("Line")
        self._line_toggle.setCheckable(True)
        toggles = QHBoxLayout()
        toggles.addWidget(self._symbols_toggle)
        toggles.addWidget(self._line_toggle)

        default = PickStyle()
        self._symbol_size = _spin_box(default.symbol_size, 0.5, 50, " px")
        self._line_width = _spin_box(default.line_width, 0.5, 20, " px")
        self._symbol_color = ColorButton(default.symbol_color)
        self._line_color = ColorButton(default.line_color)

        form.addRow("Toggle", toggles)
        form.addRow("Symbol size", self._symbol_size)
        form.addRow("Line width", self._line_width)
        form.addRow("Symbol color", self._symbol_color)
        form.addRow("Line color", self._line_color)

        for toggle in (self._symbols_toggle, self._line_toggle):
            toggle.toggled.connect(self._on_layout_changed)
        for spin_box in (self._symbol_size, self._line_width):
            spin_box.valueChanged.connect(self._on_layout_changed)
        for color_button in (self._symbol_color, self._line_color):
            color_button.colorChanged.connect(self._on_layout_changed)
        return box

    def _build_interpretation_box(self) -> QGroupBox:
        box = self._interpretation_box = QGroupBox("Interpretation")
        grid = QGridLayout()
        grid.addWidget(QLabel("Wave / Side"), 0, 0)
        grid.addWidget(QLabel("Left"), 0, 1, 1, 2, Qt.AlignmentFlag.AlignCenter)
        grid.addWidget(QLabel("Right"), 0, 3, 1, 2, Qt.AlignmentFlag.AlignCenter)

        self._fit_buttons: dict[str, QPushButton] = {}
        for row, (title, left, right) in enumerate(WAVES, start=1):
            grid.addWidget(_wave_label(title, left), row, 0)
            for column, name, side in ((1, left, "left"), (3, right, "right")):
                radio = QRadioButton()
                # Clicking the selected line again unselects it
                radio.setAutoExclusive(False)
                radio.setToolTip(f"Draw {title} ({side})")
                fit = QPushButton("Fit")
                fit.setToolTip(f"Least-squares fit of {title} ({side}) to the picks between its "
                               f"intersections (F)")
                fit.clicked.connect(partial(self._on_fit, name))
                grid.addWidget(radio, row, column)
                grid.addWidget(fit, row, column + 1)
                self._tool_buttons[name] = radio
                self._fit_buttons[name] = fit

        self._fit_all_button = QPushButton("Fit all")
        self._fit_all_button.setToolTip("Fit every drawn line, from the shot outwards")
        self._fit_all_button.clicked.connect(self._on_fit_all)

        layout = QVBoxLayout(box)
        layout.addLayout(grid)
        layout.addWidget(self._fit_all_button)
        return box

    def _build_velocities_box(self) -> QGroupBox:
        box = self._velocities_box = QGroupBox("Velocities (m/s)")
        grid = QGridLayout(box)
        for column, header in enumerate(("Left", "Right", "Average"), start=1):
            grid.addWidget(QLabel(header), 0, column, Qt.AlignmentFlag.AlignRight)

        # Left, right and average labels for each pair of drawers
        self._velocity_labels: dict[tuple[str, str], list[QLabel]] = {}
        for row, (title, left, right) in enumerate(WAVES, start=1):
            grid.addWidget(_wave_label(title, left), row, 0)
            labels = [QLabel() for _ in range(3)]
            for column, label in enumerate(labels, start=1):
                label.setAlignment(Qt.AlignmentFlag.AlignRight)
                grid.addWidget(label, row, column)
            self._velocity_labels[left, right] = labels
        return box

    def _update_state(self) -> None:
        line_loaded = self.line is not None
        self._line_menu.setEnabled(self.project is not None)
        for widget in (self._toolbar, self._layout_box, self._interpretation_box,
                       self._velocities_box):
            widget.setEnabled(line_loaded)
        title = "refrac"
        if self.project is not None:
            title += f" — {self.project.title}"
        if self.line is not None:
            title += f" / {self.line.title}"
        self.setWindowTitle(title)

    # Project and line

    def set_project(self, project: Project | None) -> None:
        self.set_line(None)
        self.project = project
        self._update_state()

    def set_line(self, line: Line | None) -> None:
        if self.line is not None:
            self._unload_line()
        self.line = line
        if line is not None:
            self._load_line()
        self._update_state()

    def _load_line(self) -> None:
        line = self.line

        self.pick_chart = PickChart()
        self.pick_chart.set_pick_style(self._pick_style())
        self.pick_chart.drawingsChanged.connect(self._on_drawings_changed)
        self.pick_chart.activeToolChanged.connect(self._sync_tool_buttons)
        self.pick_chart.zoomedChanged.connect(self._zoom_hint.setVisible)

        self.layer_chart = LayerChart(line.stations, line.shots_metadata)
        self.layer_chart.shotClicked.connect(self.try_change_shot)

        self._charts.addWidget(self.pick_chart)
        self._charts.addWidget(self.layer_chart)
        self._charts.setSizes([1, 1])

        try:
            self._load_layer_thicknesses()
        except (OSError, ValueError) as e:
            self._show_error("Unable to load layer thicknesses", str(e))

        self._go_spin.setMaximum(line.shot_amount)
        self.main_shot_index = 0
        self.amount_loaded_shots = 1
        self._update_plot()
        self._on_drawings_changed()

    def _unload_line(self) -> None:
        for chart in (self.pick_chart, self.layer_chart):
            chart.setParent(None)
            chart.deleteLater()
        self.pick_chart = None
        self.layer_chart = None
        self.main_shot = None
        self.loaded_shots = []
        self.last_saved_draw_points = None
        for label in (self._seq_num_label, self._source_station_label, self._coordinates_label):
            label.clear()
        self._sync_tool_buttons(None)
        self._zoom_hint.hide()
        self._on_drawings_changed()

    def _load_layer_thicknesses(self) -> None:
        for metadata, interpretation in zip(self.line.shots_metadata,
                                            self.line.load_all_interpretations()):
            thicknesses = interpretation[2:5]
            if (thicknesses[0] != UNDEFINED
                    and not any(math.isnan(thickness) for thickness in thicknesses)):
                self.layer_chart.plot_layer_thickness(thicknesses, metadata.station())

    # Shots

    def try_change_shot(self, shot_index: int) -> None:
        """Loads another shot, first asking to save the drawings if they changed."""
        if self.line is None:
            return
        if not self._draw_points_unchanged():
            answer = self._ask_save_changes()
            if answer == QMessageBox.StandardButton.Cancel:
                return
            if answer == QMessageBox.StandardButton.Save:
                self.save_plot()
        self.main_shot_index = shot_index
        self._update_plot()

    def _draw_points_unchanged(self) -> bool:
        draw_points = self.pick_chart.get_draw_points()
        if self.last_saved_draw_points is None:
            return not any(map(is_point_defined, draw_points))
        return draw_points == self.last_saved_draw_points

    def _ask_save_changes(self) -> QMessageBox.StandardButton:
        box = QMessageBox(QMessageBox.Icon.Question, "Unsaved changes",
                          "Save changes to current shot?", parent=self)
        box.setInformativeText("Your changes will be permanently lost if you don't save them")
        box.setStandardButtons(QMessageBox.StandardButton.Save | QMessageBox.StandardButton.Discard
                               | QMessageBox.StandardButton.Cancel)
        box.button(QMessageBox.StandardButton.Discard).setText("Don't save")
        box.setDefaultButton(QMessageBox.StandardButton.Save)
        box.exec()
        return box.standardButton(box.clickedButton())

    def _update_plot(self) -> None:
        """Plots the shots around main_shot_index and seeds the line drawers with the nearest
        interpretation saved at or before the main shot."""
        try:
            self._load_shots()
            self._plot_loaded_shots()
            self.last_saved_draw_points = self._first_previous_draw_points()
            if self.last_saved_draw_points is not None:
                self.pick_chart.set_draw_points(self.last_saved_draw_points)
        except (OSError, ValueError, IndexError) as e:
            self._show_error("Cannot load shots", str(e))

    def _update_plot_except_line_drawers(self) -> None:
        try:
            self._load_shots()
            self._plot_loaded_shots()
        except (OSError, ValueError, IndexError) as e:
            self._show_error("Cannot load shots", str(e))

    def _load_shots(self) -> None:
        index, amount = self.main_shot_index, self.amount_loaded_shots
        half = (amount - 1) // 2
        first = max(0, index - half)
        last = min(self.line.shot_amount - 1, index + half)
        self.loaded_shots = [self.line.load_shot(i) for i in range(first, last + 1)]
        self._main_position = index - first
        self.main_shot = self.loaded_shots[self._main_position]

        if amount == 1:
            self._seq_num_label.setText(str(index + 1))
            self._source_station_label.setText(str(self.main_shot.sou_stat))
        else:
            self._seq_num_label.setText(f"{first + 1} to {last + 1}")
            self._source_station_label.setText(
                f"{self.loaded_shots[0].sou_stat} to {self.loaded_shots[-1].sou_stat}")
        self._coordinates_label.setText(f"({self.main_shot.sou_x:.2f}, {self.main_shot.sou_y:.2f})")
        self._go_spin.setValue(index + 1)
        self._shots_loaded_spin.setValue(amount)

    def _plot_loaded_shots(self) -> None:
        self.pick_chart.plot(self.loaded_shots, self._main_position)
        self.layer_chart.plot_shot_in_surface(self.main_shot)

    def _first_previous_draw_points(self) -> list[Point] | None:
        for shot_index in range(self.main_shot_index, -1, -1):
            draw_points = self.line.load_draw_points(shot_index)
            if any(map(is_point_defined, draw_points)):
                return draw_points
        return None

    def save_plot(self) -> None:
        """Saves the main shot's picks, drawings and interpretation, and plots its interfaces."""
        if self.line is None:
            return
        index = self.main_shot_index
        chart = self.pick_chart
        try:
            self.line.save_shot(chart.get_main_shot(), index)

            self.last_saved_draw_points = chart.get_draw_points()
            self.line.save_draw_points(self.last_saved_draw_points, index)

            intercepts = chart.head_intercepts()
            thicknesses, velocities = compute_interpretation(chart.slopes(), intercepts)
            if any(math.isnan(thickness) for thickness in thicknesses):
                self._show_error("Cannot compute layer thickness",
                                 "There is an incoherent interpretation")
            else:
                self.layer_chart.plot_layer_thickness(thicknesses, self.main_shot.station())

            self.line.save_interpretation(
                [self.main_shot.seq_num, self.main_shot.sou_stat, *thicknesses, *velocities,
                 *(intercepts[name] for name in HEAD_NAMES), *chart.intersection_xs()],
                index)
        except (OSError, ValueError) as e:
            self._show_error("Cannot save plot", str(e))

    # Event handlers

    def _on_new_project(self) -> None:
        project_dir = NewProjectDialog.ask(self)
        if project_dir is None:
            return
        try:
            self.set_project(Project.create(project_dir))
        except OSError as e:
            self._show_error("Cannot create project", str(e))

    def _on_open_project(self) -> None:
        project_dir = QFileDialog.getExistingDirectory(self, "Open Project", str(Path.home()))
        if project_dir:
            self.set_project(Project(Path(project_dir)))

    def _on_new_line(self) -> None:
        answer = NewLineDialog.ask(self)
        if answer is None:
            return
        line_title, picks_file = answer
        try:
            line = self.project.create_line(line_title, picks_file)
        except (OSError, ValueError) as e:
            self._show_error("Cannot create line", str(e))
            return
        self.set_line(line)

    def _on_open_line(self) -> None:
        line_dir = QFileDialog.getExistingDirectory(self, "Open Line", str(self.project.home_dir))
        if not line_dir:
            return
        try:
            line = self.project.open_line(Path(line_dir))
        except (OSError, ValueError) as e:
            self._show_error("Cannot open line", str(e))
            return
        self.set_line(line)

    def _on_previous_shot(self) -> None:
        if self.main_shot_index > 0:
            self.try_change_shot(self.main_shot_index - 1)

    def _on_next_shot(self) -> None:
        if self.main_shot_index < self.line.shot_amount - 1:
            self.try_change_shot(self.main_shot_index + 1)

    def _on_go_to_shot(self) -> None:
        # The spin box range keeps the index within the line
        shot_index = self._go_spin.value() - 1
        if shot_index != self.main_shot_index:
            self.try_change_shot(shot_index)

    def _on_apply_shots_loaded(self) -> None:
        amount = self._shots_loaded_spin.value()
        if amount % 2 == 0:
            # An odd amount keeps the main shot centred
            amount += 1
        if amount != self.amount_loaded_shots:
            self.amount_loaded_shots = amount
            self._update_plot_except_line_drawers()
        self._shots_loaded_spin.setValue(self.amount_loaded_shots)

    def _pick_style(self) -> PickStyle:
        return PickStyle(
            symbols_visible=self._symbols_toggle.isChecked(),
            symbol_size=self._symbol_size.value(),
            symbol_color=self._symbol_color.color,
            line_visible=self._line_toggle.isChecked(),
            line_width=self._line_width.value(),
            line_color=self._line_color.color)

    def _on_layout_changed(self) -> None:
        symbols_visible = self._symbols_toggle.isChecked()
        line_visible = self._line_toggle.isChecked()
        self._symbol_size.setEnabled(symbols_visible)
        self._symbol_color.setEnabled(symbols_visible)
        self._line_width.setEnabled(line_visible)
        self._line_color.setEnabled(line_visible)
        if self.pick_chart is not None:
            self.pick_chart.set_pick_style(self._pick_style())

    def _on_tool_toggled(self, tool: str, checked: bool) -> None:
        if self.pick_chart is None:
            return
        if checked:
            self.pick_chart.set_active_tool(tool)
        elif self.pick_chart.active_tool == tool:
            self.pick_chart.set_active_tool(None)

    def _sync_tool_buttons(self, active_tool: str | None) -> None:
        for tool, button in self._tool_buttons.items():
            with QSignalBlocker(button):
                button.setChecked(tool == active_tool)

    def _on_drawings_changed(self) -> None:
        chart = self.pick_chart
        drawn = {name: chart is not None and chart.drawer(name).drawn for name in DRAWER_NAMES}
        for name, button in self._fit_buttons.items():
            button.setEnabled(drawn[name])
        self._fit_all_button.setEnabled(any(drawn.values()))

        slopes = chart.slopes() if chart is not None else dict.fromkeys(DRAWER_NAMES, UNDEFINED)
        for (left, right), (left_label, right_label, average_label) in self._velocity_labels.items():
            left_label.setText(_velocity_text(velocity(slopes[left])))
            right_label.setText(_velocity_text(velocity(slopes[right])))
            average_label.setText(_velocity_text(average_velocity(slopes[left], slopes[right])))

    def _on_fit(self, name: str) -> None:
        self.pick_chart.fit(name)

    def _on_fit_all(self) -> None:
        self.pick_chart.fit_all()

    def _on_fit_shortcut(self) -> None:
        if self.pick_chart is not None and self._interpretation_box.isEnabled():
            self.pick_chart.fit_selected()

    def _on_escape(self) -> None:
        if self.pick_chart is not None:
            self.pick_chart.reset_zoom()

    def _show_error(self, header: str, content: str) -> None:
        box = QMessageBox(QMessageBox.Icon.Critical, "Error", header, parent=self)
        box.setInformativeText(content)
        box.exec()


class ColorButton(QPushButton):
    """Button showing a color, which opens a color dialog to change it."""

    colorChanged = Signal(str)

    def __init__(self, color: str, parent: QWidget | None = None):
        super().__init__(parent)
        self.color = color
        self.clicked.connect(self._choose)
        self._show_color()

    def _choose(self) -> None:
        chosen = QColorDialog.getColor(QColor(self.color), self)
        if chosen.isValid():
            self.color = chosen.name()
            self._show_color()
            self.colorChanged.emit(self.color)

    def _show_color(self) -> None:
        text_color = "black" if QColor(self.color).lightness() > 127 else "white"
        self.setText(self.color)
        self.setStyleSheet(f"background-color: {self.color}; color: {text_color};")


def _titled(title: str, label: QLabel) -> QWidget:
    """A value label under its title, for the toolbar."""
    widget = QWidget()
    layout = QVBoxLayout(widget)
    layout.setContentsMargins(6, 0, 6, 0)
    layout.setSpacing(0)
    for item in (QLabel(title), label):
        item.setAlignment(Qt.AlignmentFlag.AlignCenter)
        layout.addWidget(item)
    return widget


def _wave_label(title: str, drawer_name: str) -> QLabel:
    label = QLabel(title)
    label.setStyleSheet(f"color: {wave_style(drawer_name)[0]};")
    return label


def _spin_box(value: float, minimum: float, maximum: float, suffix: str) -> QDoubleSpinBox:
    spin_box = QDoubleSpinBox()
    spin_box.setRange(minimum, maximum)
    spin_box.setSingleStep(0.5)
    spin_box.setDecimals(1)
    spin_box.setSuffix(suffix)
    spin_box.setValue(value)
    return spin_box


def _velocity_text(value: float) -> str:
    return "" if value == UNDEFINED else f"{value:.2f}"
