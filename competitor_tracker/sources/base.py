"""Source interface shared by all signal providers."""

from __future__ import annotations

from abc import ABC, abstractmethod

from ..http import HttpClient
from ..models import Competitor, Signal
from ..storage import Store


class Source(ABC):
    """Base class for anything that can produce :class:`Signal` objects.

    A source is given the shared HTTP client, persistent store, and the active
    settings, then asked to ``collect`` signals for a single competitor.
    """

    name: str = "source"

    def __init__(self, http: HttpClient, store: Store, settings):
        self.http = http
        self.store = store
        self.settings = settings

    @abstractmethod
    def collect(self, product_key: str, competitor: Competitor) -> list[Signal]:
        """Return new signals for ``competitor`` under ``product_key``."""
        raise NotImplementedError
