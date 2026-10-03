"""Dialogs for creating projects and lines."""

from pathlib import Path

from PySide6.QtWidgets import (QDialog, QDialogButtonBox, QFileDialog, QFormLayout, QHBoxLayout,
                               QLineEdit, QPushButton, QWidget)


class NewProjectDialog(QDialog):
    """Asks for the name and location of a new project. The project folder must not exist yet,
    or be an empty directory."""

    def __init__(self, parent: QWidget | None = None, initial_dir: Path = Path.home()):
        super().__init__(parent)
        self.setWindowTitle("New Project")

        self._name = QLineEdit()
        self._location = QLineEdit(str(initial_dir))
        self._folder = QLineEdit()
        self._folder.setReadOnly(True)
        browse = QPushButton("...")
        browse.clicked.connect(self._on_browse)

        location_row = QHBoxLayout()
        location_row.addWidget(self._location)
        location_row.addWidget(browse)
        form = QFormLayout(self)
        form.addRow("Project Name:", self._name)
        form.addRow("Project Location:", location_row)
        form.addRow("Project Folder:", self._folder)

        self._buttons = QDialogButtonBox(
            QDialogButtonBox.StandardButton.Ok | QDialogButtonBox.StandardButton.Cancel)
        self._buttons.accepted.connect(self.accept)
        self._buttons.rejected.connect(self.reject)
        form.addRow(self._buttons)

        self._name.textChanged.connect(self._update_folder)
        self._location.textChanged.connect(self._update_folder)
        self._update_folder()
        self.resize(500, self.sizeHint().height())

    @classmethod
    def ask(cls, parent: QWidget | None = None) -> Path | None:
        """Shows the dialog and returns the project folder, or None if cancelled."""
        dialog = cls(parent)
        return dialog.project_dir() if dialog.exec() == QDialog.DialogCode.Accepted else None

    def project_dir(self) -> Path:
        return Path(self._folder.text())

    def _update_folder(self) -> None:
        name, location = self._name.text(), self._location.text()
        folder = str(Path(location) / name) if name.strip() and location.strip() else ""
        self._folder.setText(folder)
        ok = self._buttons.button(QDialogButtonBox.StandardButton.Ok)
        ok.setEnabled(bool(folder) and _is_dir_available(Path(folder)))

    def _on_browse(self) -> None:
        start = Path(self._folder.text())
        if not (self._folder.text() and start.is_dir()):
            start = Path.home()
        chosen = QFileDialog.getExistingDirectory(self, "Select Project Location", str(start))
        if chosen:
            self._location.setText(chosen)


def _is_dir_available(path: Path) -> bool:
    """Whether the path can become a project folder: it doesn't exist or is an empty directory."""
    try:
        return not path.exists() or (path.is_dir() and not any(path.iterdir()))
    except OSError:
        return False


class NewLineDialog(QDialog):
    """Asks for the title of a new line and the picks file to load into it."""

    def __init__(self, parent: QWidget | None = None):
        super().__init__(parent)
        self.setWindowTitle("New Processing Line")

        self._title = QLineEdit("untitled")
        self._picks_file = QLineEdit()
        browse = QPushButton("...")
        browse.clicked.connect(self._on_browse)

        picks_row = QHBoxLayout()
        picks_row.addWidget(self._picks_file)
        picks_row.addWidget(browse)
        form = QFormLayout(self)
        form.addRow("Line Title:", self._title)
        form.addRow("Picks File:", picks_row)

        self._buttons = QDialogButtonBox(
            QDialogButtonBox.StandardButton.Ok | QDialogButtonBox.StandardButton.Cancel)
        self._buttons.accepted.connect(self.accept)
        self._buttons.rejected.connect(self.reject)
        form.addRow(self._buttons)

        self._title.textChanged.connect(self._validate)
        self._picks_file.textChanged.connect(self._validate)
        self._validate()
        self._title.selectAll()
        self.resize(500, self.sizeHint().height())

    @classmethod
    def ask(cls, parent: QWidget | None = None) -> tuple[str, Path] | None:
        """Shows the dialog and returns the line title and picks file, or None if cancelled."""
        dialog = cls(parent)
        if dialog.exec() != QDialog.DialogCode.Accepted:
            return None
        return dialog._title.text().strip(), Path(dialog._picks_file.text())

    def _validate(self) -> None:
        picks_file = self._picks_file.text()
        valid = bool(self._title.text().strip()) and bool(picks_file) and Path(picks_file).is_file()
        self._buttons.button(QDialogButtonBox.StandardButton.Ok).setEnabled(valid)

    def _on_browse(self) -> None:
        current = Path(self._picks_file.text())
        start = current.parent if self._picks_file.text() and current.is_file() else Path.home()
        chosen, _ = QFileDialog.getOpenFileName(self, "Open Picks File", str(start))
        if chosen:
            self._picks_file.setText(chosen)
