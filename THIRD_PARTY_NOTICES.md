# Third-party notices

KireiBox contains source code, assets, and prebuilt Native libraries derived from third-party projects. KireiBox's root `LICENSE` applies to KireiBox's own GPL-3.0-covered work; it does not replace or erase the copyright notices and licenses of bundled third-party components.

## Primary upstream projects

| Component | Upstream / provenance | License note |
| --- | --- | --- |
| YukiHub core and imported engine integration | [YukiHub](https://gitcode.com/xm486/YukiHub), with the public GPL-3.0 snapshot in [RinneMobile](https://github.com/Weiss-UltimateSavior/RinneMobile) | GPL-3.0; retained source modifications are distributed under the same terms |
| KRKR / Kirikiri runtime | [Kirikiroid2Yuri](https://github.com/YuriSizuku/Kirikiroid2Yuri) | The repository is a combined work containing GPL and separately licensed components; retain and follow its upstream notices |
| ONScripter runtime | [OnscripterYuri](https://github.com/YuriSizuku/OnscripterYuri) | GPL-2.0 and licenses of bundled SDL, Lua, JPEG, bzip2 and font components |
| Tyrano launch integration | [RinneMobile engine snapshot](https://github.com/Weiss-UltimateSavior/RinneMobile/tree/main/engine) | Retains the upstream project notices and the licenses of any game/runtime content it loads |
| Artemis launch integration | [RinneMobile engine snapshot](https://github.com/Weiss-UltimateSavior/RinneMobile/tree/main/engine) | Distributed as an upstream-derived binary snapshot; KireiBox does not claim authorship |
| FFmpeg / IJKPlayer binaries | Bundled through the [RinneMobile Native snapshot](https://github.com/Weiss-UltimateSavior/RinneMobile/tree/main/engine/src/main/jniLibs/arm64-v8a) | FFmpeg, IJKPlayer and their linked components retain their respective LGPL/GPL-compatible notices |
| DroidSansFallback font | Android Open Source Project-derived font bundled by the upstream engine | Retains its upstream Apache-2.0/font notices |

The Native files under `engine/src/main/jniLibs/arm64-v8a` match the public RinneMobile engine snapshot except `libgame.so`: that file is the stock KRKR 1.3.9 runtime from Kirikiroid2Yuri. The ONS runtime files under `engine/src/main/assets/libs/Yuri_0.7.6` originate from OnscripterYuri as integrated by RinneMobile/YukiHub.

`libBugly.so` and the unused `sf.sf2` asset are intentionally excluded from this repository and from builds.

## Native binary inventory

The following SHA-256 values identify every committed prebuilt `.so` file. Sizes are decimal bytes.

| Repository path | Size | SHA-256 |
| --- | ---: | --- |
| `engine/src/main/jniLibs/arm64-v8a/libandroidx.graphics.path.so` | 10096 | `41e9a793c43a0f4fddb19e33f346bace464f30f888ba7b9eaf96294ea115bfb6` |
| `engine/src/main/jniLibs/arm64-v8a/libartemis.so` | 9292144 | `cb2b58d8080b29529b27a8453f602646ab5f13ae5d4acc59b673c27c27e2d9cc` |
| `engine/src/main/jniLibs/arm64-v8a/libartemis-compatible.so` | 7506728 | `48e72b81092d30144af4a8362eb47f04c71d2e5f03539a9c5b39030d4f72b8ee` |
| `engine/src/main/jniLibs/arm64-v8a/libartemis-compatible-v2.so` | 9304328 | `75ffde0ad3a8c58fd82a3f797131686eb66ed7468a7db6452be7932718c44b35` |
| `engine/src/main/jniLibs/arm64-v8a/libbytehook.so` | 324440 | `b9e411557b38142d847681fd879b15bb84b038e4339fc8cd5ca2621d252ef429` |
| `engine/src/main/assets/libs/Yuri_0.7.6/libbz2.so` | 69248 | `22aabe3cb98a6e073571b372558e17a07d0ced8cd198cdca1bfc13e2b1a3da04` |
| `engine/src/main/jniLibs/arm64-v8a/libffmpeg.so` | 10412024 | `bd89c37d6a20f313febaed24dc1b9713ded76cef7ea2395800f765ea6782849a` |
| `engine/src/main/jniLibs/arm64-v8a/libgame.so` | 27929688 | `ded611b9018cfca425e97d5f8aaaa5dff809c4bacefb66ba77806372ddb52b38` |
| `engine/src/main/jniLibs/arm64-v8a/libgame126.so` | 24566480 | `c7d05f84d45deb3906987da7d02af288c1ea3bb949e4d6e5cbe80c09441b2560` |
| `engine/src/main/jniLibs/arm64-v8a/libgame134.so` | 27375912 | `71f616c68db94caa7afccc60a20ba7ea48cbb562c762dbd91dd533593b5de238` |
| `engine/src/main/jniLibs/arm64-v8a/libijkffmpeg.so` | 11971808 | `6c1560dcd8fe81f9348d14f719c49e8e9d8b23c2bd4d142b2e4f23de71445972` |
| `engine/src/main/jniLibs/arm64-v8a/libijkplayer.so` | 364944 | `64c47d1d1d3a820524f33214238b11c2a6d40a9b25036624f07a71a0abf80666` |
| `engine/src/main/jniLibs/arm64-v8a/libijksdl.so` | 481352 | `b469d1fca009d52774d93c348a95ed5929d5ddd3c09bc95c6fd7b11af9e13d3e` |
| `engine/src/main/assets/libs/Yuri_0.7.6/libjpeg.so` | 252296 | `58209b9e097facf39c0ba2630a8f1a05fdc489ea724e466ad111c181e8c4ff3e` |
| `engine/src/main/assets/libs/Yuri_0.7.6/liblua.so` | 286280 | `250c1dfc14a5315e91dd702af23412ef6a05f0a054e8de59899c151809bd4ecf` |
| `engine/src/main/jniLibs/arm64-v8a/libmmkv.so` | 597000 | `3850acf55b2c29bd3a3714ac84b7ab6ef402fedc61d9646209dd273d604d1bd1` |
| `engine/src/main/jniLibs/arm64-v8a/libONSPatch.so` | 6216064 | `ab99923f0be7a69c1ffd52dcc526d6ab80c88481a2a9ca2bac1574aeb103cfe1` |
| `engine/src/main/assets/libs/Yuri_0.7.6/libonsyuri.so` | 745280 | `16edab5ddfcdc0e32c85e73fc51a1d81762f7965cafbb456dde131a5b1aca7fa` |
| `engine/src/main/assets/libs/Yuri_0.7.6/libSDL2.so` | 1411840 | `3d1358ef74d5079bf69ce4f3926a7f489b80b583694aa209189b9dbe42a11508` |
| `engine/src/main/jniLibs/arm64-v8a/libSDL2.so` | 1138192 | `4138eaf40d578e4e4146c07df9ec8dca218c375d395f10b6093aad9b06553903` |
| `engine/src/main/assets/libs/Yuri_0.7.6/libSDL2_image.so` | 171184 | `240e0a0668adfafdced09c839b884f06a13b45f10d47a968a5cb8a1fbeb5e28c` |
| `engine/src/main/assets/libs/Yuri_0.7.6/libSDL2_mixer.so` | 216792 | `fa1ca8dcbb06654485a10a40f90a6ecc7287471a597f9e58acd78baf1a2ef2bb` |
| `engine/src/main/assets/libs/Yuri_0.7.6/libSDL2_ttf.so` | 749512 | `4dd8dfa46bd77b294a5bdc2ed26ee238068c232d7b555e44ccfd7639972dee19` |
| `engine/src/main/jniLibs/arm64-v8a/libshadowhook.so` | 79464 | `5afb272443eafb5c6ccabf4bb019e49840aa0a22698930164a8ac0fd25de684a` |
| `engine/src/main/jniLibs/arm64-v8a/libshadowhook_nothing.so` | 1112 | `f2cc6a6f94d7361be915daafff1d25b7b3af34972e7308ac540982cfd608bd32` |

Inventory total: 25 files, 141,474,208 bytes (134.92 MiB).

## Dependency metadata

Java/Kotlin dependencies and their exact versions are declared in `gradle/libs.versions.toml`. Their own license terms continue to apply. Downstream redistributors should review the complete dependency graph and preserve any required notices when producing binaries.

If a provenance or license statement here is incomplete, please open an issue with the affected file path and supporting upstream information so it can be corrected without removing the original notice.

## Account authorization and metadata services

- [AppAuth for Android](https://github.com/openid/AppAuth-Android), Apache-2.0, is used for browser authorization and PKCE. KireiBox registers its own public clients.
- [NextMoe / 鲲Galgame](https://developer.nextmoe.dev/) provides aggregated catalog references and cover choices. Cover source, size, and content rating are preserved in the local metadata cache.
- [Bangumi](https://bangumi.tv/), [Hikarinagi](https://www.hikarinagi.org/), and [VNDB](https://vndb.org/) retain ownership and terms for their catalog data and images. Connecting an account does not transfer credentials to image hosts.
