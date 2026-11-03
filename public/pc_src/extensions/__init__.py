"""
MovieBox All-In-One Multi-Server Extensions Package
Contains modular extensions for all 6 streaming servers:
1. MovieBoxExtension (Server 1: Movies & TV Series)
2. VskitExtension (Server 2: VSKit Short Dramas)
3. LookrExtension (Server 3: Lookr Movies & Series)
4. StoryTvExtension (Server 4: Story TV Mini Dramas)
5. FreeReelsExtension (Server 5: Free Reels Trending)
6. JioSaavnExtension (Server 6: JioSaavn Music & Podcasts)
"""

from .base_extension import BaseServerExtension
from .moviebox_extension import MovieBoxExtension
from .vskit_extension import VskitExtension
from .lookr_extension import LookrExtension
from .storytv_extension import StoryTvExtension
from .freereels_extension import FreeReelsExtension
from .jiosaavn_extension import JioSaavnExtension
from .extension_manager import ExtensionManager

__all__ = [
    "BaseServerExtension",
    "MovieBoxExtension",
    "VskitExtension",
    "LookrExtension",
    "StoryTvExtension",
    "FreeReelsExtension",
    "JioSaavnExtension",
    "ExtensionManager"
]
