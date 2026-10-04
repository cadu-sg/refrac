import math

import numpy as np
import pytest
from PySide6.QtCore import Qt
from PySide6.QtWidgets import QMessageBox

from refrac.dialogs import NewLineDialog
from refrac.main_window import MainWindow
from refrac.pick_chart import DRAWER_NAMES, ERASER, ZOOM, PickChart
from refrac.storage import Line, Project
from refrac.structs import UNDEFINED_POINT

LEFT = Qt.MouseButton.LeftButton
RIGHT = Qt.MouseButton.RightButton

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


def test_single_active_tool(window):
    chart = window.pick_chart
    window._tool_buttons["head1L"].setChecked(True)
    assert chart.active_tool == "head1L" and chart.drawer("head1L").enabled
    window._tool_buttons[ZOOM].setChecked(True)
    assert chart.active_tool == ZOOM
    assert not window._tool_buttons["head1L"].isChecked() and not chart.drawer("head1L").enabled
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
    window.set_line(Line.open(home_dir))
    assert window.layer_chart.interface_points == saved_interfaces


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


@pytest.fixture
def chart(qtbot):
    chart = PickChart()
    qtbot.addWidget(chart)
    return chart


def test_standard_drawer_clicks(chart):
    drawer = chart.drawer("head1R")
    chart.set_active_tool("head1R")
    drawer.click((30, 40), LEFT)
    assert drawer.p1 == (30, 40) and not drawer.drawn
    drawer.click((90, 70), LEFT)
    assert drawer.drawn and drawer.slope == pytest.approx(0.5) and drawer.intercept == 25

    # Right click erases the nearest end point, a left click draws it again
    drawer.click((80, 0), RIGHT)
    assert (drawer.p1, drawer.p2, drawer.drawn) == ((30, 40), None, False)
    assert drawer.slope == 0
    drawer.click((100, 75), LEFT)
    assert drawer.p2 == (100, 75) and drawer.drawn

    # Deactivating with a single point placed discards it
    drawer.click((0, 0), RIGHT)
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
    assert drawer.p1 == (0, 0) and drawer._mirror.isVisible()
    drawer.click((-20, 30), LEFT)
    assert drawer.drawn and drawer.slope == -1.5 and not drawer._mirror.isVisible()
    assert drawer.draggable_points() == [(1, (-20, 30))]
    drawer.click((-5, 0), RIGHT)
    assert drawer.p2 is None and not drawer.drawn
    chart.set_active_tool(None)
    assert drawer.p1 is None
    assert chart.get_draw_points() == [UNDEFINED_POINT] * 14


def test_draw_points_packing(chart):
    points = [(float(i + 1), float(i + 2)) for i in range(14)]
    chart.set_draw_points(points)
    assert chart.get_draw_points() == points
    assert chart.drawer("directL").p1 == (0, 0) and chart.drawer("directL").p2 == points[6]
    assert chart.drawer("head3R").p2 == points[13]
    # Incomplete lines are not part of the draw points
    chart.set_active_tool("head3L")
    chart.drawer("head3L").click((-50, 50), RIGHT)
    assert chart.get_draw_points()[:2] == [UNDEFINED_POINT] * 2


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
