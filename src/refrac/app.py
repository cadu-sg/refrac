"""Application entry point."""

import sys

import pyqtgraph as pg
from PySide6.QtWidgets import QApplication

from refrac.main_window import MainWindow


def configure_plots() -> None:
    """Global pyqtgraph options, to set before creating any chart."""
    pg.setConfigOptions(background="w", foreground="k", antialias=True)


def main() -> None:
    app = QApplication(sys.argv)
    app.setApplicationName("refrac")
    configure_plots()
    window = MainWindow()
    window.show()
    sys.exit(app.exec())
