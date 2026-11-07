#ifndef LIBRETRO_H__
#define LIBRETRO_H__

#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

#define RETRO_API_VERSION 1

// Tipos de dispositivos SNES (Estándar y Multitap)
#define RETRO_DEVICE_NONE     0
#define RETRO_DEVICE_JOYPAD   1
#define RETRO_DEVICE_MOUSE    2
#define RETRO_DEVICE_LIGHTGUN 4

// Botones exactos del mando de Super Nintendo (SNES)
#define RETRO_DEVICE_ID_JOYPAD_B        0
#define RETRO_DEVICE_ID_JOYPAD_Y        1
#define RETRO_DEVICE_ID_JOYPAD_SELECT   2
#define RETRO_DEVICE_ID_JOYPAD_START    3
#define RETRO_DEVICE_ID_JOYPAD_UP       4
#define RETRO_DEVICE_ID_JOYPAD_DOWN     5
#define RETRO_DEVICE_ID_JOYPAD_LEFT     6
#define RETRO_DEVICE_ID_JOYPAD_RIGHT    7
#define RETRO_DEVICE_ID_JOYPAD_A        8
#define RETRO_DEVICE_ID_JOYPAD_X        9
#define RETRO_DEVICE_ID_JOYPAD_L       10
#define RETRO_DEVICE_ID_JOYPAD_R       11

// Regiones y tipos de memoria (SRAM de batería y WRAM)
#define RETRO_REGION_NTSC 0
#define RETRO_REGION_PAL  1

#define RETRO_MEMORY_SAVE_RAM 0
#define RETRO_MEMORY_RTC      1
#define RETRO_MEMORY_SYSTEM_RAM 2
#define RETRO_MEMORY_VIDEO_RAM  3

// Comandos de entorno Libretro usados por Snes9x / bsnes
#define RETRO_ENVIRONMENT_GET_OVERSCAN               2
#define RETRO_ENVIRONMENT_GET_CAN_DUPE               3
#define RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY       9
#define RETRO_ENVIRONMENT_SET_PIXEL_FORMAT          10
#define RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS     11
#define RETRO_ENVIRONMENT_GET_VARIABLE              15
#define RETRO_ENVIRONMENT_SET_VARIABLES             16
#define RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE       17
#define RETRO_ENVIRONMENT_SET_SUPPORT_NO_GAME       18
#define RETRO_ENVIRONMENT_GET_LOG_INTERFACE         27
#define RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY        31
#define RETRO_ENVIRONMENT_SET_MEMORY_MAPS           36
#define RETRO_ENVIRONMENT_SET_GEOMETRY              37
#define RETRO_ENVIRONMENT_GET_INPUT_BITMASKS        51 | 0x10000

enum retro_pixel_format {
    RETRO_PIXEL_FORMAT_0RGB1555 = 0,
    RETRO_PIXEL_FORMAT_XRGB8888 = 1,
    RETRO_PIXEL_FORMAT_RGB565   = 2,
    RETRO_PIXEL_FORMAT_UNKNOWN  = 0x7fffffff
};

struct retro_variable {
    const char *key;
    const char *value;
};

struct retro_system_info {
    const char *library_name;
    const char *library_version;
    const char *valid_extensions;
    bool        need_fullpath;
    bool        block_extract;
};

struct retro_game_geometry {
    unsigned base_width;
    unsigned base_height;
    unsigned max_width;
    unsigned max_height;
    float    aspect_ratio;
};

struct retro_system_timing {
    double fps;
    double sample_rate;
};

struct retro_system_av_info {
    struct retro_game_geometry geometry;
    struct retro_system_timing timing;
};

struct retro_game_info {
    const char *path;
    const void *data;
    size_t      size;
    const char *meta;
};

typedef bool (*retro_environment_t)(unsigned cmd, void *data);
typedef void (*retro_video_refresh_t)(const void *data, unsigned width, unsigned height, size_t pitch);
typedef void (*retro_audio_sample_t)(int16_t left, int16_t right);
typedef size_t (*retro_audio_sample_batch_t)(const int16_t *data, size_t frames);
typedef void (*retro_input_poll_t)(void);
typedef int16_t (*retro_input_state_t)(unsigned port, unsigned device, unsigned index, unsigned id);

// Punteros a funciones exportadas por libsnes9x_libretro_android.so
typedef void (*fn_retro_set_environment)(retro_environment_t);
typedef void (*fn_retro_set_video_refresh)(retro_video_refresh_t);
typedef void (*fn_retro_set_audio_sample)(retro_audio_sample_t);
typedef void (*fn_retro_set_audio_sample_batch)(retro_audio_sample_batch_t);
typedef void (*fn_retro_set_input_poll)(retro_input_poll_t);
typedef void (*fn_retro_set_input_state)(retro_input_state_t);
typedef void (*fn_retro_init)(void);
typedef void (*fn_retro_deinit)(void);
typedef unsigned (*fn_retro_api_version)(void);
typedef void (*fn_retro_get_system_info)(struct retro_system_info *);
typedef void (*fn_retro_get_system_av_info)(struct retro_system_av_info *);
typedef void (*fn_retro_set_controller_port_device)(unsigned port, unsigned device);
typedef void (*fn_retro_reset)(void);
typedef void (*fn_retro_run)(void);
typedef size_t (*fn_retro_serialize_size)(void);
typedef bool (*fn_retro_serialize)(void *data, size_t size);
typedef bool (*fn_retro_unserialize)(const void *data, size_t size);
typedef bool (*fn_retro_load_game)(const struct retro_game_info *game);
typedef void (*fn_retro_unload_game)(void);
typedef unsigned (*fn_retro_get_region)(void);
typedef void *(*fn_retro_get_memory_data)(unsigned id);
typedef size_t (*fn_retro_get_memory_size)(unsigned id);

#ifdef __cplusplus
}
#endif

#endif // LIBRETRO_H__
