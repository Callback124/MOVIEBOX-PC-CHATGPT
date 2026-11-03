"""
Extension Manager Registry
Loads and provides a unified interface to access all 6 MovieBox streaming server extensions.
"""

from .moviebox_extension import MovieBoxExtension
from .vskit_extension import VskitExtension
from .lookr_extension import LookrExtension
from .storytv_extension import StoryTvExtension
from .freereels_extension import FreeReelsExtension
from .jiosaavn_extension import JioSaavnExtension


class ExtensionManager:
    _instance = None

    def __init__(self):
        self._extensions = {
            "server_1": MovieBoxExtension(),
            "server_2": VskitExtension(),
            "server_3": LookrExtension(),
            "server_4": StoryTvExtension(),
            "server_5": FreeReelsExtension(),
            "server_6": JioSaavnExtension(),
            # Also allow lookup by server name
            "moviebox": None,
            "vskit": None,
            "lookr": None,
            "storytv": None,
            "story tv": None,
            "freereels": None,
            "free reels": None,
            "jiosaavn": None
        }
        self._extensions["moviebox"] = self._extensions["server_1"]
        self._extensions["vskit"] = self._extensions["server_2"]
        self._extensions["lookr"] = self._extensions["server_3"]
        self._extensions["storytv"] = self._extensions["server_4"]
        self._extensions["story tv"] = self._extensions["server_4"]
        self._extensions["freereels"] = self._extensions["server_5"]
        self._extensions["free reels"] = self._extensions["server_5"]
        self._extensions["jiosaavn"] = self._extensions["server_6"]

    @classmethod
    def get_instance(cls):
        if cls._instance is None:
            cls._instance = ExtensionManager()
        return cls._instance

    @classmethod
    def get(cls, key):
        mgr = cls.get_instance()
        k = str(key).strip().lower()
        return mgr._extensions.get(k) or mgr._extensions.get("server_1")

    @classmethod
    def list_all(cls):
        mgr = cls.get_instance()
        return [
            mgr._extensions["server_1"],
            mgr._extensions["server_2"],
            mgr._extensions["server_3"],
            mgr._extensions["server_4"],
            mgr._extensions["server_5"],
            mgr._extensions["server_6"]
        ]
