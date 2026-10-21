# Experimental safe library boundary

This identified library root intentionally contains no Sol modules yet.
The mode-isolation subset supports only parameterless int/void functions with
literal returns and checked local source imports. All std imports are rejected,
including filesystem substitutes. Do not copy the legacy raw library here or
advertise it as safe. New modules require reviewed safety contracts/checkers.
