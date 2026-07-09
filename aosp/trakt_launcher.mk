# Include Trakt TV as a preinstalled app in your product image.
# Add to your device.mk with:  $(call inherit-product, path/to/trakt_launcher.mk)
# or just copy the PRODUCT_PACKAGES line into device.mk.
#
# For Trakt TV to be the DEFAULT Home, it should be the only app in the image
# with a HOME intent-filter. Either omit the stock launcher from PRODUCT_PACKAGES,
# or override config_defaultLauncherComponent via an overlay/RRO. See aosp/README.md.

PRODUCT_PACKAGES += \
    TraktTvLauncher

# Example: if your base inherits a stock TV launcher you want to drop, remove it
# from the inherited product or blacklist it, e.g.
#   PRODUCT_PACKAGES := $(filter-out TvLauncher,$(PRODUCT_PACKAGES))
