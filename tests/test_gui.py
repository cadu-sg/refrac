import csv
import math

import numpy as np
import pytest
from PySide6.QtCore import QEvent, QPointF, Qt
from PySide6.QtTest import QTest
from PySide6.QtWidgets import QApplication, QFileDialog, QMessageBox

from refrac.dialogs import NewLineDialog
from refrac.export import EXPORT_COLUMNS
from refrac.layer_chart import DEFAULT_DEPTH, LAYER_COLORMAPS, LayerChart
from refrac.main_window import MainWindow
from refrac.pick_chart import DRAWER_NAMES, ERASER, ZOOM, PickChart
from refrac.storage import Line, Project
from refrac.structs import UNDEFINED, UNDEFINED_POINT, Station

MIDDLE_SHOT = 12  # station 221
# Offsets well inside each branch of the middle shot's first arrivals
SEGMENTS = {
    "head3L": (-340, -200), "head2L": (-160, -110), "head1L": (-85, -35), "directL": (0, -15),
    "directR": (0, 15), "head1R": (35, 90), "head2R": (115, 160), "head3R": (200, 340),
}


@pytest.fixture
def window(qtbot, tmp_path, synthetic_picks):
    window = MainWindow()
    qtbot.addWidget(window)
    window.errors = []
    window._show_error = lambda header, content: window.errors.append((header, content))
    window.warnings = []
    window._show_warning = lambda header, content: window.warnings.append((header, content))
    project = Project.create(tmp_path / "project")
    window.set_project(project)
    window.set_line(project.create_line("line", synthetic_picks))
    return window


def draw_points_on_picks(shot) -> list:
    """Draw points of lines through the picks of each branch of the shot."""
    offsets = np.array([pick.offset for pick in shot.picks])
    times = np.array([pick.travel_time for pick in shot.picks])
    points = []
    for name in DRAWER_NAMES:
        x1, x2 = SEGMENTS[name]
        p1, p2 = [(x, float(np.interp(x, offsets, times))) for x in (x1, x2)]
        points += [p2] if name.startswith("direct") else [p1, p2]
    return points


def test_line_loads_first_shot(window):
    assert window._seq_num_label.text() == "1"
    assert window._source_station_label.text() == "101"
    assert window._coordinates_label.text() == "(500000.00, 7500000.00)"
    assert window._toolbar.isEnabled() and window._line_menu.isEnabled()
    assert not window._fit_all_button.isEnabled()
    assert window.pick_chart.get_draw_points() == [UNDEFINED_POINT] * 14


def test_interpret_save_and_seed_next_shot(window):
    window.try_change_shot(MIDDLE_SHOT)
    assert window._source_station_label.text() == "221"
    chart = window.pick_chart
    chart.set_draw_points(draw_points_on_picks(window.main_shot))
    assert all(button.isEnabled() for button in window._fit_buttons.values())
    assert window._velocity_labels["directL", "directR"][2].text() != ""
    assert sum(marker.isVisible() for marker in chart._markers) == 6

    chart.fit_all()
    velocities = {name: 1000 / abs(slope) for name, slope in chart.slopes().items()}
    assert 550 < velocities["directL"] < 650 and 550 < velocities["directR"] < 650
    assert 1500 < velocities["head1R"] < 2200
    assert 2500 < velocities["head2R"] < 3500
    assert 3500 < velocities["head3R"] < 5500

    window.save_plot()
    assert window.errors == []
    row = window.line.load_all_interpretations()[MIDDLE_SHOT]
    assert row[:2] == [13, 221]
    assert 5 < row[2] < 12 and 10 < row[3] < 40 and 10 < row[4] < 60  # thicknesses
    assert all(value != 0 for value in row[9:21])  # intercepts and intersections
    assert window.line.load_draw_points(MIDDLE_SHOT) == chart.get_draw_points()
    station_x, elevation1, elevation2, elevation3 = window.layer_chart.interface_points[221]
    assert station_x == 500600.0 and elevation1 > elevation2 > elevation3

    # The next shot has no lines yet, so it starts from the saved ones, unchanged
    saved_points = chart.get_draw_points()
    window._ask_save_changes = pytest.fail
    window.try_change_shot(MIDDLE_SHOT + 1)
    assert window._seq_num_label.text() == "14"
    assert chart.get_draw_points() == saved_points


@pytest.mark.parametrize("answer, expected_shot, saved", [
    (QMessageBox.StandardButton.Cancel, MIDDLE_SHOT, False),
    (QMessageBox.StandardButton.Discard, MIDDLE_SHOT + 1, False),
    (QMessageBox.StandardButton.Save, MIDDLE_SHOT + 1, True),
])
def test_unsaved_changes_prompt(window, answer, expected_shot, saved):
    window.try_change_shot(MIDDLE_SHOT)
    window.pick_chart.set_draw_points(draw_points_on_picks(window.main_shot))
    window._ask_save_changes = lambda: answer
    window.try_change_shot(MIDDLE_SHOT + 1)
    assert window.main_shot_index == expected_shot
    assert (window.line.load_draw_points(MIDDLE_SHOT) != [UNDEFINED_POINT] * 14) == saved


def test_export_interpretation(window, tmp_path, monkeypatch):
    assert window._export_action.isEnabled()
    window.try_change_shot(MIDDLE_SHOT)
    window.pick_chart.set_draw_points(draw_points_on_picks(window.main_shot))
    window.pick_chart.fit_all()
    window.save_plot()

    path = tmp_path / "export"
    monkeypatch.setattr(QFileDialog, "getSaveFileName", lambda *args: (str(path), ""))
    window._export_action.trigger()
    assert window.errors == []
    with open(path.with_suffix(".csv"), newline="", encoding="utf-8") as file:
        header, *rows = list(csv.reader(file))
    assert tuple(header) == EXPORT_COLUMNS
    ((ffid, sou_sloc, *values),) = rows
    assert (ffid, sou_sloc) == ("13", "221")
    assert all(values)
    _, elevation1, elevation2, elevation3 = window.layer_chart.interface_points[221]
    assert [float(z) for z in values[-3:]] == pytest.approx(
        [elevation1, elevation2, elevation3], abs=0.005)

    window.set_line(None)
    assert not window._export_action.isEnabled()


def test_export_after_cancelling_unsaved_changes_does_nothing(window, monkeypatch):
    window.pick_chart.set_draw_points(draw_points_on_picks(window.main_shot))
    window._ask_save_changes = lambda: QMessageBox.StandardButton.Cancel
    monkeypatch.setattr(QFileDialog, "getSaveFileName", pytest.fail)
    window._export_action.trigger()


def test_export_without_interpreted_shots_writes_nothing(window, monkeypatch):
    messages = []
    monkeypatch.setattr(QMessageBox, "information", lambda *args: messages.append(args[2]))
    monkeypatch.setattr(QFileDialog, "getSaveFileName", pytest.fail)
    window._export_action.trigger()
    assert len(messages) == 1 and messages[0].startswith("No interpreted shots")


def test_erased_picks_are_saved(window):
    picks_before = len(window.main_shot.picks)
    window.pick_chart.erase_picks(100, 200, 0, 1000)
    window.save_plot()
    window.try_change_shot(1)
    window.try_change_shot(0)
    picks = window.main_shot.picks
    assert len(picks) == picks_before - 21  # offsets 100 to 200, every 5 m
    assert all(not 100 <= pick.offset <= 200 for pick in picks)


def test_shots_loaded_window(window):
    window.try_change_shot(MIDDLE_SHOT)
    window._shots_loaded_spin.setValue(4)
    window._on_apply_shots_loaded()
    assert window.amount_loaded_shots == 5
    assert window._seq_num_label.text() == "11 to 15"
    assert window._source_station_label.text() == "201 to 241"
    assert window.pick_chart.get_main_shot().sou_stat == 221

    # Clamped at the start of the line
    window.try_change_shot(0)
    assert window._seq_num_label.text() == "1 to 3"


def test_go_to_shot_and_layer_chart_navigation(window):
    window._go_spin.setValue(25)
    window._on_go_to_shot()
    assert window._source_station_label.text() == "340"
    window._on_next_shot()
    assert window.main_shot_index == 24

    window.layer_chart.shotClicked.emit(3)
    assert window._source_station_label.text() == "131"
    source_line = window.layer_chart._source_line
    assert source_line.isVisible() and source_line.value() == window.main_shot.sou_x


def test_single_active_tool(window):
    chart = window.pick_chart
    window._tool_buttons["directL"].setChecked(True)
    assert chart.active_tool == "directL" and chart.drawer("directL").enabled
    window._tool_buttons[ZOOM].setChecked(True)
    assert chart.active_tool == ZOOM
    assert not window._tool_buttons["directL"].isChecked() and not chart.drawer("directL").enabled
    window._tool_buttons[ZOOM].setChecked(False)
    assert chart.active_tool is None

    # Loading draw points deselects the line being drawn
    window._tool_buttons["directR"].setChecked(True)
    chart.set_draw_points([UNDEFINED_POINT] * 14)
    assert chart.active_tool is None and not window._tool_buttons["directR"].isChecked()

    window._tool_buttons[ERASER].setChecked(True)
    window.set_line(None)
    assert not window._tool_buttons[ERASER].isChecked()
    assert not window._toolbar.isEnabled()


def test_close_project_unloads_line(window):
    window.set_project(None)
    assert window.line is None and window.pick_chart is None
    assert not window._line_menu.isEnabled()


def test_reopened_line_plots_saved_interfaces(window, tmp_path):
    window.try_change_shot(MIDDLE_SHOT)
    window.pick_chart.set_draw_points(draw_points_on_picks(window.main_shot))
    window.save_plot()
    saved_interfaces = window.layer_chart.interface_points
    home_dir = window.line.home_dir
    x, elevation1, _, _ = saved_interfaces[221]
    window.layer_chart.show_velocity_at(x, elevation1 - 1)
    saved_velocity = hover_velocity_text(window.layer_chart)
    window.set_line(Line.open(home_dir))
    assert window.layer_chart.interface_points == saved_interfaces
    window.layer_chart.show_velocity_at(x, elevation1 - 1)
    assert hover_velocity_text(window.layer_chart) == saved_velocity


def hover_velocity_text(layer_chart: LayerChart) -> str:
    return layer_chart._hover_text.textItem.toPlainText().strip()


def test_layer_chart_shows_the_velocity_of_the_layer_under_the_mouse(window):
    window.try_change_shot(MIDDLE_SHOT)
    window.pick_chart.set_draw_points(draw_points_on_picks(window.main_shot))
    window.pick_chart.fit_all()
    window.save_plot()
    velocities = window.line.load_all_interpretations()[MIDDLE_SHOT][5:9]
    layer_chart = window.layer_chart
    x, elevation1, elevation2, elevation3 = layer_chart.interface_points[221]
    # The deepest layer reaches down to the bottom of the chart
    tops = [window.main_shot.sou_elev, elevation1, elevation2, elevation3]
    bottoms = [elevation1, elevation2, elevation3, layer_chart.bottom]
    for y, layer in [(elevation1 + 1, 1), ((elevation1 + elevation2) / 2, 2),
                     ((elevation2 + elevation3) / 2, 3), (elevation3 - 50, 4)]:
        # Only x matters within a layer, and station 221 is the only one interpreted
        assert layer_chart.velocity_target(x + 1000, y) == (221, layer)
        layer_chart.show_velocity_at(x + 1000, y)
        assert layer_chart._hover_segment.isVisible() and layer_chart._hover_text.isVisible()
        segment_x, segment_y = layer_chart._hover_segment.getData()
        assert list(segment_x) == [x, x]
        assert list(segment_y) == [tops[layer - 1], bottoms[layer - 1]]
        assert hover_velocity_text(layer_chart) == f"{velocities[layer - 1]:.0f} m/s"

    layer_chart._on_mouse_moved(QPointF(-1e6, -1e6))  # outside the plot
    assert not layer_chart._hover_segment.isVisible()
    layer_chart.show_velocity_at(x, elevation1)
    layer_chart.leaveEvent(QEvent(QEvent.Type.Leave))
    assert not layer_chart._hover_segment.isVisible() and not layer_chart._hover_text.isVisible()


def interface_lines(layer_chart: LayerChart) -> list[tuple[list[float], list[float]]]:
    lines = [interface.getData() for interface in layer_chart._interfaces]
    return [([] if xs is None else list(xs), [] if ys is None else list(ys)) for xs, ys in lines]


def point_lightnesses(layer_chart: LayerChart, layer: int) -> list[float]:
    brushes = layer_chart._interfaces[layer - 1].scatter.data["brush"]
    return [brush.color().lightnessF() for brush in brushes]


def test_layer_colors_follow_the_velocities_of_each_layer(qtbot):
    stations = [Station(num, 100.0 * num, 0.0, 100.0) for num in range(4)]
    layer_chart = LayerChart(stations, [])
    qtbot.addWidget(layer_chart)
    middle = LAYER_COLORMAPS[0].map(0.5, mode="qcolor").lightnessF()

    layer_chart.plot_layer_thickness([10.0, 0.0, 0.0], [600.0, 2000.0, 0.0, 0.0], stations[0])
    assert point_lightnesses(layer_chart, 1) == pytest.approx([middle])

    # Faster is darker
    layer_chart.plot_layer_thickness([10.0, 0.0, 0.0], [500.0, 2000.0, 0.0, 0.0], stations[1])
    layer_chart.plot_layer_thickness([10.0, 0.0, 0.0], [700.0, 2000.0, 0.0, 0.0], stations[2])
    lightness600, lightness500, lightness700 = point_lightnesses(layer_chart, 1)
    assert lightness500 > lightness600 > lightness700
    assert lightness600 == pytest.approx(middle)

    # A velocity difference much smaller than the velocities keeps close to the middle
    for station in stations[1:3]:
        layer_chart.plot_layer_thickness([UNDEFINED] * 3, [UNDEFINED] * 4, station)
    layer_chart.plot_layer_thickness([10.0, 0.0, 0.0], [601.0, 2000.0, 0.0, 0.0], stations[3])
    assert point_lightnesses(layer_chart, 1) == pytest.approx([middle] * 2, abs=0.02)


def view_bottom(layer_chart: LayerChart) -> float:
    return layer_chart._plot_widget.getPlotItem().getViewBox().viewRange()[1][0]


def test_layer_chart_bottom_defaults_below_the_lowest_station_and_can_be_set(qtbot):
    stations = [Station(num, 100.0 * num, 0.0, 100.5 + num) for num in range(4)]
    layer_chart = LayerChart(stations, [])
    qtbot.addWidget(layer_chart)
    assert layer_chart.bottom == view_bottom(layer_chart) == pytest.approx(100 - DEFAULT_DEPTH)

    layer_chart.set_bottom(-50.0)
    # A deeper interface doesn't move the bottom
    layer_chart.plot_layer_thickness([500.0, 0.0, 0.0], [600.0, 0.0, 0.0, 0.0], stations[0])
    assert layer_chart.bottom == view_bottom(layer_chart) == pytest.approx(-50)


def test_layer_chart_bottom_spin_box(window):
    lowest = min(station.elev for station in window.line.stations)
    assert window._layer_bottom.value() == math.floor(lowest) - DEFAULT_DEPTH
    assert view_bottom(window.layer_chart) == pytest.approx(window._layer_bottom.value())

    window._layer_bottom.setValue(-300)
    assert window.layer_chart.bottom == -300
    assert view_bottom(window.layer_chart) == pytest.approx(-300)


def fill_outline(fill) -> list[tuple[float, float]]:
    polygon = fill.polygon()
    return [(polygon.at(i).x(), polygon.at(i).y()) for i in range(polygon.size())]


def fill_colors(fill) -> list[int]:
    return [color.rgb() for _, color in fill.brush().gradient().stops()]


def point_colors(layer_chart: LayerChart, layer: int) -> list[int]:
    brushes = layer_chart._interfaces[layer - 1].scatter.data["brush"]
    return [brush.color().rgb() for brush in brushes]


def fills_by_outline(layer_chart: LayerChart) -> dict:
    return {tuple(fill_outline(fill)): fill for fill in layer_chart._fills}


def colormap_ends(layer: int) -> list[int]:
    return [LAYER_COLORMAPS[layer - 1].map(position, mode="qcolor").rgb() for position in (0, 1)]


def test_layers_are_filled_with_a_gradient_between_the_stations(qtbot):
    stations = [Station(1, 0.0, 0.0, 100.0), Station(2, 50.0, 0.0, 110.0),
                Station(3, 100.0, 0.0, 100.0), Station(4, 200.0, 0.0, 100.0)]
    layer_chart = LayerChart(stations, [])
    qtbot.addWidget(layer_chart)
    layer_chart.set_bottom(-100.0)
    layer_chart.plot_layer_thickness([10.0, 0.0, 0.0], [500.0, 2000.0, 0.0, 0.0], stations[0])
    assert layer_chart._fills == []

    # Layer 1, whose top follows the surface, and below it layer 2 down to the bottom, as
    # interface 2 is at a single station
    layer_chart.plot_layer_thickness([10.0, 20.0, 0.0], [600.0, 2000.0, 3000.0, 0.0],
                                     stations[2])
    fills = fills_by_outline(layer_chart)
    layer1 = ((0, 100), (50, 110), (100, 100), (100, 90), (0, 90))
    deep_layer2 = ((0, 90), (100, 90), (100, -100), (0, -100))
    assert set(fills) == {layer1, deep_layer2}
    assert fill_colors(fills[layer1]) == colormap_ends(1)
    assert fill_colors(fills[deep_layer2]) == point_colors(layer_chart, 2) * 2

    # Below interface 2, layer 3 reaches down to the bottom
    layer_chart.plot_layer_thickness([10.0, 30.0, 0.0], [600.0, 2500.0, 3000.0, 0.0],
                                     stations[3])
    layer2 = ((100, 90), (200, 90), (200, 60), (100, 70))
    deep_layer3 = ((100, 70), (200, 60), (200, -100), (100, -100))
    assert set(fills_by_outline(layer_chart)) == {
        layer1, ((100, 100), (200, 100), (200, 90), (100, 90)), deep_layer2, layer2, deep_layer3}
    assert fill_colors(fills_by_outline(layer_chart)[layer2]) == point_colors(layer_chart, 2)

    # The deepest layers follow the bottom
    layer_chart.set_bottom(-50.0)
    assert ((100, 70), (200, 60), (200, -50), (100, -50)) in fills_by_outline(layer_chart)

    # With interface 3 at both stations, layer 4 is below it instead
    layer_chart.plot_layer_thickness([10.0, 20.0, 5.0], [600.0, 2000.0, 3000.0, 4000.0],
                                     stations[2])
    layer_chart.plot_layer_thickness([10.0, 30.0, 10.0], [600.0, 2500.0, 3000.0, 5000.0],
                                     stations[3])
    fills = fills_by_outline(layer_chart)
    deep_layer4 = ((100, 65), (200, 50), (200, -50), (100, -50))
    assert deep_layer4 in fills and ((100, 70), (200, 60), (200, -50), (100, -50)) not in fills
    assert fill_colors(fills[deep_layer4]) == colormap_ends(4)


def test_layer_chart_velocity_target(qtbot):
    stations = [Station(1, 0.0, 0.0, 100.0), Station(2, 100.0, 0.0, 100.0),
                Station(3, 200.0, 0.0, 100.0)]
    layer_chart = LayerChart(stations, [])
    qtbot.addWidget(layer_chart)
    assert layer_chart.velocity_target(0.0, 50.0) is None

    # Below the only interface there is, the deepest layer, layer 2
    layer_chart.plot_layer_thickness([10.0, 0.0, 0.0], [500.0, 2000.0, 0.0, 0.0], stations[0])
    assert layer_chart.velocity_target(0.0, 95.0) == (1, 1)
    assert layer_chart.velocity_target(0.0, 50.0) == (1, 2)

    # Where interface 2 isn't drawn, below interface 1 is still layer 2
    layer_chart.plot_layer_thickness([10.0, 20.0, 0.0], [500.0, 2000.0, 3000.0, 0.0],
                                     stations[1])
    layer_chart.plot_layer_thickness([10.0, 20.0, 0.0], [500.0, 2000.0, 3000.0, 0.0],
                                     stations[2])
    assert interface_lines(layer_chart) == [
        ([0.0, 100.0, 200.0], [90.0, 90.0, 90.0]), ([100.0, 200.0], [70.0, 70.0]), ([], [])]
    assert layer_chart.velocity_target(0.0, 50.0) == (1, 2)
    assert layer_chart.velocity_target(40.0, 50.0) == (1, 2)
    assert layer_chart.velocity_target(100.0, 80.0) == (2, 2)
    assert layer_chart.velocity_target(100.0, 50.0) == (2, 3)
    # Beyond the last station, as at the last station
    assert layer_chart.velocity_target(500.0, 50.0) == (3, 3)

    for station in stations:
        layer_chart.plot_layer_thickness([0.0] * 3, [0.0] * 4, station)
    assert layer_chart.velocity_target(0.0, 50.0) is None


def test_interfaces_and_source_hang_from_the_sou_elev_column(window, tmp_path, sou_elev_picks):
    window.set_line(window.project.create_line("sou_elev", sou_elev_picks))
    window.try_change_shot(MIDDLE_SHOT)
    assert list(window.layer_chart._source.getData()[1]) == [113.0]  # 100 + FFID
    window.pick_chart.set_draw_points(draw_points_on_picks(window.main_shot))
    window.save_plot()
    h1 = window.line.load_all_interpretations()[MIDDLE_SHOT][2]
    _, elevation1, _, _ = window.layer_chart.interface_points[221]
    assert elevation1 == pytest.approx(113.0 - h1)


def test_undetectable_decimal_separator_is_reported(window, tmp_path, synthetic_picks,
                                                    monkeypatch):
    picks = tmp_path / "mixed.dat"
    lines = synthetic_picks.read_text().splitlines()
    lines[20] = lines[20].replace(".", ",")
    picks.write_text("\n".join(lines) + "\n")
    monkeypatch.setattr(NewLineDialog, "ask", lambda parent=None: ("mixed", picks))
    line = window.line
    window._on_new_line()
    ((header, content),) = window.errors
    assert header == "Cannot create line"
    assert "decimal separator, '.' on line 2 and ',' on line 21" in content
    assert window.line is line
    assert not (window.project.home_dir / "mixed").exists()


def test_lines_are_drawn_from_the_shot_outwards(window):
    chart = window.pick_chart
    buttons = window._tool_buttons
    enabled = lambda: {name for name in DRAWER_NAMES if buttons[name].isEnabled()}
    assert enabled() == {"directL", "directR"}
    chart.set_active_tool("head2L")
    assert chart.active_tool is None

    # Each side unlocks on its own
    chart.drawer("directL").set_points((0, 0), (-15, 25))
    assert enabled() == {"directL", "directR", "head1L"}
    buttons["head1R"].setChecked(True)
    assert chart.active_tool is None and not buttons["head1R"].isChecked()
    chart.drawer("head1L").set_points((-85, 70), (-35, 45))
    assert enabled() == {"directL", "directR", "head1L", "head2L"}


def test_inner_line_cannot_be_removed(window, qtbot):
    window.show()
    qtbot.waitExposed(window)
    chart = window.pick_chart
    chart.zoom_to(-200, 0, 0, 120)
    lines = {"directL": ((0, 0), (-15, 25)), "head1L": ((-85, 70), (-35, 45)),
             "head2L": ((-160, 100), (-110, 85))}
    for name, line in lines.items():
        chart.drawer(name).set_points(*line)
    head1L = chart.drawer("head1L")
    saved_points = chart.get_draw_points()

    # One right click erases an end point, to place it again
    chart._right_click((-36, 44))
    assert chart.active_tool == "head1L"
    assert head1L.p1 == (-85, 70) and head1L.p2 is None
    chart._left_click((-30, 42))
    assert head1L.drawn and head1L.p2 == (-30, 42)

    # The second right click, which would remove the line, is refused wherever it is
    chart._right_click((-31, 41))
    chart._right_click((-150, 5))
    assert head1L.p1 == (-85, 70) and head1L.p2 is None
    assert window.warnings == [("Cannot remove Refracted 1 (left)",
                                "Refracted 2 (left) is drawn outside it. Remove the lines starting "
                                "from the outermost one")]
    # Meanwhile the line keeps its last end points, and gets them back when deselected
    assert chart.get_draw_points()[4:6] == [(-85, 70), (-30, 42)]
    chart.set_active_tool(ZOOM)
    assert head1L.drawn and (head1L.p1, head1L.p2) == ((-85, 70), (-30, 42))

    # The direct wave stays too
    chart._right_click((-14, 24))
    assert chart.active_tool == "directL" and chart.drawer("directL").p2 is None
    chart._right_click((-150, 5))
    assert window.warnings[-1][0] == "Cannot remove Direct (left)"
    chart.set_active_tool(None)
    assert chart.drawer("directL").p2 == (-15, 25)

    # The outermost line can be removed, then the one inside it
    chart._right_click((-158, 99))
    chart._right_click((-50, 5))
    assert chart.drawer("head2L").p1 is None
    chart._right_click((-84, 70))
    chart._right_click((-150, 5))
    assert head1L.p1 is None and chart.active_tool == "head1L"
    assert len(window.warnings) == 2
    assert not window._tool_buttons["head2L"].isEnabled()
    assert chart.get_draw_points() != saved_points


@pytest.fixture
def chart(qtbot):
    chart = PickChart()
    qtbot.addWidget(chart)
    return chart


def test_standard_drawer_clicks(chart):
    chart.drawer("directR").set_points((0, 0), (20, 50))
    drawer = chart.drawer("head1R")
    chart.set_active_tool("head1R")
    drawer.click((30, 40))
    assert drawer.p1 == (30, 40) and drawer.pending and not drawer.drawn
    drawer.click((90, 70))
    assert drawer.drawn and drawer.slope == pytest.approx(0.5) and drawer.intercept == 25

    # Erasing an end point leaves the line pending, a left click draws it again
    drawer.erase_end_point(1, (80, 0))
    assert (drawer.p1, drawer.p2, drawer.drawn) == ((30, 40), None, False) and drawer.pending
    assert drawer.slope == 0
    drawer.click((100, 75))
    assert drawer.p2 == (100, 75) and drawer.drawn
    drawer.erase_end_point(0, (0, 0))  # the other end point is kept as p1
    assert drawer.p1 == (100, 75) and drawer.p2 is None
    drawer.discard_point()
    assert drawer.p1 is None and not drawer.pending

    # Deactivating with a single point placed discards it
    drawer.click((30, 40))
    chart.set_active_tool(None)
    assert drawer.p1 is None and drawer.p2 is None


def test_standard_drawer_drag(chart):
    drawer = chart.drawer("head2L")
    drawer.set_points((-100, 80), (-50, 60))
    drawer.start_drag(0)  # dragging p1 makes it p2
    assert drawer.p2 == (-100, 80) and not drawer.drawn
    drawer.drag_to((-120, 90))
    drawer.finish_drag()
    assert drawer.drawn and drawer.p1 == (-50, 60) and drawer.p2 == (-120, 90)


def test_origin_fixed_drawer(chart):
    drawer = chart.drawer("directL")
    chart.set_active_tool("directL")
    assert not drawer.pending and drawer._mirror.isVisible()
    drawer.click((-20, 30))
    assert drawer.drawn and drawer.slope == -1.5 and not drawer._mirror.isVisible()
    assert drawer.draggable_points() == [(1, (-20, 30))]
    drawer.erase_end_point(1, (-5, 0))
    assert drawer.p1 == (0, 0) and drawer.p2 is None and drawer.pending and not drawer.drawn
    drawer.discard_point()
    assert drawer.p1 is None and drawer._mirror.isVisible()
    chart.set_active_tool(None)
    assert not drawer._mirror.isVisible()
    assert chart.get_draw_points() == [UNDEFINED_POINT] * 14


def test_draw_points_packing(chart):
    points = [(float(i + 1), float(i + 2)) for i in range(14)]
    chart.set_draw_points(points)
    assert chart.get_draw_points() == points
    assert chart.drawer("directL").p1 == (0, 0) and chart.drawer("directL").p2 == points[6]
    assert chart.drawer("head3R").p2 == points[13]
    # Incomplete lines are not part of the draw points
    chart.set_active_tool("head3L")
    chart.drawer("head3L").erase_end_point(0, (-50, 50))
    assert chart.get_draw_points()[:2] == [UNDEFINED_POINT] * 2


def test_right_click_erases_the_nearest_point_of_any_line(chart, qtbot):
    chart.resize(800, 600)
    chart.show()
    qtbot.waitExposed(chart)
    chart.zoom_to(0, 200, 0, 120)
    refused = []
    chart.lineRemovalRefused.connect(lambda name, outer: refused.append((name, outer)))
    chart._right_click((100, 50))  # without lines, nothing to erase
    assert chart.active_tool is None

    lines = {"directR": ((0, 0), (15, 25)), "head1R": ((35, 45), (85, 70)),
             "head2R": ((110, 85), (160, 100))}
    for name, line in lines.items():
        chart.drawer(name).set_points(*line)
    chart.set_active_tool("head2R")

    # The nearest end point is erased whichever line is selected, and its line selected
    head1R = chart.drawer("head1R")
    chart._right_click((37, 47))
    assert chart.active_tool == "head1R"
    assert head1R.p1 == (85, 70) and head1R.p2 is None
    # The next right click goes to that line, even next to another one
    chart._right_click((159, 99))
    assert refused == [("head1R", "head2R")] and head1R.pending
    chart._left_click((30, 42))
    assert head1R.drawn and chart.drawer("head2R").drawn

    # Whatever the tool
    chart.set_active_tool(ZOOM)
    chart._right_click((16, 24))
    assert chart.active_tool == "directR" and chart.drawer("directR").pending


def test_zoom_and_reset(chart):
    chart.zoom_to(-10, 10, 0, 5)
    assert chart.zoomed
    chart.reset_zoom()
    assert not chart.zoomed
    (x_min, x_max), (y_min, y_max) = chart._view_box.viewRange()
    assert x_min <= 0 <= x_max and y_min <= 0 <= y_max


def test_incoherent_interpretation_is_reported(window):
    window.try_change_shot(MIDDLE_SHOT)
    chart = window.pick_chart
    chart.set_draw_points(draw_points_on_picks(window.main_shot))
    # Refracted 1 slower than the direct wave
    chart.drawer("head1L").set_points((-85, 200), (-35, 100))
    chart.drawer("head1R").set_points((35, 100), (90, 200))
    window.save_plot()
    assert [header for header, _ in window.errors] == ["Cannot compute layer thickness"]
    assert math.isnan(window.line.load_all_interpretations()[MIDDLE_SHOT][2])


def test_shot_keyboard_shortcuts(window, qtbot):
    window.show()
    window.activateWindow()
    qtbot.waitUntil(window.isActiveWindow, timeout=2000)
    window.try_change_shot(MIDDLE_SHOT)
    QTest.keyClick(QApplication.focusWidget(), Qt.Key.Key_Right)
    assert window.main_shot_index == MIDDLE_SHOT + 1
    QTest.keyClick(window, Qt.Key.Key_Left)
    QTest.keyClick(window, Qt.Key.Key_Left)
    assert window.main_shot_index == MIDDLE_SHOT - 1

    window.pick_chart.set_draw_points(draw_points_on_picks(window.main_shot))
    QTest.keyClick(window, Qt.Key.Key_S)
    assert window.line.load_draw_points(MIDDLE_SHOT - 1) != [UNDEFINED_POINT] * 14


def test_tool_keyboard_shortcuts(window, qtbot):
    window.show()
    window.activateWindow()
    qtbot.waitUntil(window.isActiveWindow, timeout=2000)
    QTest.keyClick(QApplication.focusWidget(), Qt.Key.Key_Z)
    assert window.pick_chart.active_tool == ZOOM
    QTest.keyClick(QApplication.focusWidget(), Qt.Key.Key_E)
    assert window.pick_chart.active_tool == ERASER
    QTest.keyClick(QApplication.focusWidget(), Qt.Key.Key_E)
    assert window.pick_chart.active_tool is None
