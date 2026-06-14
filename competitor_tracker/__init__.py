"""Competitor tracker for the D1 and D4 product lines.

A small, dependency-light toolkit that watches the major competitors of two
products (referred to throughout as ``d1`` and ``d4``) and surfaces fresh
signals about them: recent news coverage and changes to their public websites.

The product/competitor universe lives in ``config.yaml`` so the same code can
track any set of products without edits.
"""

from .models import Competitor, Product, Signal
from .tracker import Tracker

__all__ = ["Competitor", "Product", "Signal", "Tracker"]
__version__ = "0.1.0"
