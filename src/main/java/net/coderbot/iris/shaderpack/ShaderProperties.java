package net.coderbot.iris.shaderpack;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.IrisRenderSystem;
import net.coderbot.iris.gl.blending.AlphaTest;
import net.coderbot.iris.gl.blending.AlphaTestFunction;
import net.coderbot.iris.gl.blending.AlphaTestOverride;
import net.coderbot.iris.gl.blending.BlendMode;
import net.coderbot.iris.gl.blending.BlendModeFunction;
import net.coderbot.iris.gl.blending.BlendModeOverride;
import net.coderbot.iris.gl.texture.TextureScaleOverride;
import net.coderbot.iris.gl.blending.BufferBlendOverride;
import net.coderbot.iris.shaderpack.option.ShaderPackOptions;
import net.coderbot.iris.shaderpack.preprocessor.PropertiesPreprocessor;
import net.coderbot.iris.shaderpack.texture.TextureStage;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * The parsed representation of the shaders.properties file. This class is not meant to be stored permanently, rather
 * it merely exists as an intermediate step until we build up PackDirectives and ProgramDirectives objects from the
 * values in here & the values parsed from shader source code.
 */
public class ShaderProperties {
	private CloudSetting cloudSetting = CloudSetting.DEFAULT;
	private OptionalBoolean oldHandLight = OptionalBoolean.DEFAULT;
	private OptionalBoolean dynamicHandLight = OptionalBoolean.DEFAULT;
	private OptionalBoolean oldLighting = OptionalBoolean.DEFAULT;
	private OptionalBoolean shadowTerrain = OptionalBoolean.DEFAULT;
	private OptionalBoolean shadowTranslucent = OptionalBoolean.DEFAULT;
	private OptionalBoolean shadowEntities = OptionalBoolean.DEFAULT;
	private OptionalBoolean shadowPlayer = OptionalBoolean.DEFAULT;
	private OptionalBoolean shadowBlockEntities = OptionalBoolean.DEFAULT;
	private OptionalBoolean underwaterOverlay = OptionalBoolean.DEFAULT;
	private OptionalBoolean sun = OptionalBoolean.DEFAULT;
	private OptionalBoolean moon = OptionalBoolean.DEFAULT;
	private OptionalBoolean vignette = OptionalBoolean.DEFAULT;
	private OptionalBoolean backFaceSolid = OptionalBoolean.DEFAULT;
	private OptionalBoolean backFaceCutout = OptionalBoolean.DEFAULT;
	private OptionalBoolean backFaceCutoutMipped = OptionalBoolean.DEFAULT;
	private OptionalBoolean backFaceTranslucent = OptionalBoolean.DEFAULT;
	private OptionalBoolean rainDepth = OptionalBoolean.DEFAULT;
	private OptionalBoolean concurrentCompute = OptionalBoolean.DEFAULT;
	private OptionalBoolean beaconBeamDepth = OptionalBoolean.DEFAULT;
	private OptionalBoolean separateAo = OptionalBoolean.DEFAULT;
	private OptionalBoolean frustumCulling = OptionalBoolean.DEFAULT;
	private OptionalBoolean shadowCulling = OptionalBoolean.DEFAULT;
	private OptionalBoolean particlesBeforeDeferred = OptionalBoolean.DEFAULT;
	private OptionalBoolean prepareBeforeShadow = OptionalBoolean.DEFAULT;
	private List<String> sliderOptions = new ArrayList<>();
	private final Map<String, List<String>> profiles = new LinkedHashMap<>();
	private List<String> mainScreenOptions = null;
	private final Map<String, List<String>> subScreenOptions = new HashMap<>();
	private Integer mainScreenColumnCount = null;
	private final Map<String, Integer> subScreenColumnCount = new HashMap<>();
	// TODO: private Map<String, String> optifineVersionRequirements;
	// TODO: Parse custom uniforms / variables
	private final Object2ObjectMap<String, AlphaTestOverride> alphaTestOverrides = new Object2ObjectOpenHashMap<>();
	private final Object2FloatMap<String> viewportScaleOverrides = new Object2FloatOpenHashMap<>();
	private final Object2ObjectMap<String, TextureScaleOverride> textureScaleOverrides = new Object2ObjectOpenHashMap<>();
	private final Object2ObjectMap<String, BlendModeOverride> blendModeOverrides = new Object2ObjectOpenHashMap<>();
	private final Object2ObjectMap<String, ArrayList<BufferBlendOverride>> bufferBlendOverrides = new Object2ObjectOpenHashMap<>();
	private final EnumMap<TextureStage, Object2ObjectMap<String, String>> customTextures = new EnumMap<>(TextureStage.class);
	private final Object2ObjectMap<String, Object2BooleanMap<String>> explicitFlips = new Object2ObjectOpenHashMap<>();
	private String noiseTexturePath = null;
	private Object2ObjectMap<String, String> conditionallyEnabledPrograms = new Object2ObjectOpenHashMap<>();

	private ShaderProperties() {
		// empty
	}

	// TODO: Is there a better solution than having ShaderPack pass a root path to ShaderProperties to be able to read textures?
	public ShaderProperties(String contents, ShaderPackOptions shaderPackOptions, Iterable<StringPair> environmentDefines) {
		String preprocessedContents = PropertiesPreprocessor.preprocessSource(contents, shaderPackOptions, environmentDefines);

		Properties preprocessed = new OrderBackedProperties();
		Properties original = new OrderBackedProperties();
		try {
			preprocessed.load(new StringReader(preprocessedContents));
			original.load(new StringReader(contents));
		} catch (IOException e) {
			Iris.logger.error("Error loading shaders.properties!", e);
		}

		preprocessed.forEach((keyObject, valueObject) -> {
			String key = (String) keyObject;
			String value = (String) valueObject;

			if ("texture.noise".equals(key)) {
				noiseTexturePath = value;
				return;
			}

			if ("clouds".equals(key)) {
				if ("off".equals(value)) {
					cloudSetting = CloudSetting.OFF;
				} else if ("fast".equals(value)) {
					cloudSetting = CloudSetting.FAST;
				} else if ("fancy".equals(value)) {
					cloudSetting = CloudSetting.FANCY;
				} else {
					Iris.logger.error("Unrecognized clouds setting: " + value);
				}
			}

			handleBooleanDirective(key, value, "oldHandLight", bool -> oldHandLight = bool);
			handleBooleanDirective(key, value, "dynamicHandLight", bool -> dynamicHandLight = bool);
			handleBooleanDirective(key, value, "oldLighting", bool -> oldLighting = bool);
			handleBooleanDirective(key, value, "shadowTerrain", bool -> shadowTerrain = bool);
			handleBooleanDirective(key, value, "shadowTranslucent", bool -> shadowTranslucent = bool);
			handleBooleanDirective(key, value, "shadowEntities", bool -> shadowEntities = bool);
			handleBooleanDirective(key, value, "shadowPlayer", bool -> shadowPlayer = bool);
			handleBooleanDirective(key, value, "shadowBlockEntities", bool -> shadowBlockEntities = bool);
			handleBooleanDirective(key, value, "underwaterOverlay", bool -> underwaterOverlay = bool);
			handleBooleanDirective(key, value, "sun", bool -> sun = bool);
			handleBooleanDirective(key, value, "moon", bool -> moon = bool);
			handleBooleanDirective(key, value, "vignette", bool -> vignette = bool);
			handleBooleanDirective(key, value, "backFace.solid", bool -> backFaceSolid = bool);
			handleBooleanDirective(key, value, "backFace.cutout", bool -> backFaceCutout = bool);
			handleBooleanDirective(key, value, "backFace.cutoutMipped", bool -> backFaceCutoutMipped = bool);
			handleBooleanDirective(key, value, "backFace.translucent", bool -> backFaceTranslucent = bool);
			handleBooleanDirective(key, value, "rain.depth", bool -> rainDepth = bool);
			handleBooleanDirective(key, value, "allowConcurrentCompute", bool -> concurrentCompute = bool);
			handleBooleanDirective(key, value, "beacon.beam.depth", bool -> beaconBeamDepth = bool);
			handleBooleanDirective(key, value, "separateAo", bool -> separateAo = bool);
			handleBooleanDirective(key, value, "frustum.culling", bool -> frustumCulling = bool);
			handleBooleanDirective(key, value, "shadow.culling", bool -> shadowCulling = bool);
			handleBooleanDirective(key, value, "particles.before.deferred", bool -> particlesBeforeDeferred = bool);
			handleBooleanDirective(key, value, "prepareBeforeShadow", bool -> prepareBeforeShadow = bool);

			// TODO: Min optifine versions, shader options layout / appearance / profiles
			// TODO: Custom uniforms

			handlePassDirective("scale.", key, value, pass -> {
				float scale;

				try {
					scale = Float.parseFloat(value);
				} catch (NumberFormatException e) {
					Iris.logger.error("Unable to parse scale directive for " + pass + ": " + value, e);
					return;
				}

				viewportScaleOverrides.put(pass, scale);
			});

			handlePassDirective("size.buffer.", key, value, pass -> {
				String[] parts = value.split(" ");

				if (parts.length != 2) {
					Iris.logger.error("Unable to parse size.buffer directive for " + pass + ": " + value);
					return;
				}

				textureScaleOverrides.put(pass, new TextureScaleOverride(parts[0], parts[1]));
			});

			handlePassDirective("alphaTest.", key, value, pass -> {
				if ("off".equals(value)) {
					alphaTestOverrides.put(pass, AlphaTestOverride.OFF);
					return;
				}

				String[] parts = value.split(" ");

				if (parts.length > 2) {
					Iris.logger.warn("Weird alpha test directive for " + pass + " contains more parts than we expected: " + value);
				} else if (parts.length < 2) {
					Iris.logger.error("Invalid alpha test directive for " + pass + ": " + value);
					return;
				}

				Optional<AlphaTestFunction> function = AlphaTestFunction.fromString(parts[0]);

				if (!function.isPresent()) {
					Iris.logger.error("Unable to parse alpha test directive for " + pass + ", unknown alpha test function " + parts[0] + ": " + value);
					return;
				}

				float reference;

				try {
					reference = Float.parseFloat(parts[1]);
				} catch (NumberFormatException e) {
					Iris.logger.error("Unable to parse alpha test directive for " + pass + ": " + value, e);
					return;
				}

				alphaTestOverrides.put(pass, new AlphaTestOverride(new AlphaTest(function.get(), reference)));
			});

			handlePassDirective("blend.", key, value, pass -> {
				if (pass.contains(".")) {

					if (!IrisRenderSystem.supportsBufferBlending()) {
						throw new RuntimeException("Buffer blending is not supported on this platform, however it was attempted to be used!");
					}

					String[] parts = pass.split("\\.");
					int index = PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.indexOf(parts[1]);

					if (index == -1 && parts[1].startsWith("colortex")) {
						String id = parts[1].substring("colortex".length());

						try {
							index = Integer.parseInt(id);
						} catch (NumberFormatException e) {
							throw new RuntimeException("Failed to parse buffer blend!", e);
						}
					}

					if (index == -1) {
						throw new RuntimeException("Failed to parse buffer blend! index = " + index);
					}

					if ("off".equals(value)) {
						bufferBlendOverrides.computeIfAbsent(parts[0], list -> new ArrayList<>()).add(new BufferBlendOverride(index, null));
						return;
					}

					String[] modeArray = value.split(" ");
					int[] modes = new int[modeArray.length];

					int i = 0;
					for (String modeName : modeArray) {
						modes[i] = BlendModeFunction.fromString(modeName).get().getGlId();
						i++;
					}

					bufferBlendOverrides.computeIfAbsent(parts[0], list -> new ArrayList<>()).add(new BufferBlendOverride(index, new BlendMode(modes[0], modes[1], modes[2], modes[3])));

					return;
				}

				if ("off".equals(value)) {
					blendModeOverrides.put(pass, BlendModeOverride.OFF);
					return;
				}

				String[] modeArray = value.split(" ");
				int[] modes = new int[modeArray.length];

				int i = 0;
				for (String modeName : modeArray) {
					modes[i] = BlendModeFunction.fromString(modeName).get().getGlId();
					i++;
				}

				blendModeOverrides.put(pass, new BlendModeOverride(new BlendMode(modes[0], modes[1], modes[2], modes[3])));
			});

			handleProgramEnabledDirective("program.", key, value, program -> {
				conditionallyEnabledPrograms.put(program, value);
			});

			handleTwoArgDirective("texture.", key, value, (stageName, samplerName) -> {
				String[] parts = value.split(" ");

				// TODO: Support raw textures
				if (parts.length > 1) {
					Iris.logger.warn("Custom texture directive for stage " + stageName + ", sampler " + samplerName + " contains more parts than we expected: " + value);
					return;
				}

				Optional<TextureStage> optionalTextureStage = TextureStage.parse(stageName);

				if (!optionalTextureStage.isPresent()) {
					Iris.logger.warn("Unknown texture stage " + "\"" + stageName + "\"," + " ignoring custom texture directive for " + key);
					return;
				}

				TextureStage stage = optionalTextureStage.get();

				customTextures.computeIfAbsent(stage, _stage -> new Object2ObjectOpenHashMap<>())
						.put(samplerName, value);
			});

			handleTwoArgDirective("flip.", key, value, (pass, buffer) -> {
				handleBooleanValue(key, value, shouldFlip -> {
					explicitFlips.computeIfAbsent(pass, _pass -> new Object2BooleanOpenHashMap<>())
							.put(buffer, shouldFlip);
				});
			});

			// TODO: Buffer size directives
			// TODO: Conditional program enabling directives
		});

		// We need to use a non-preprocessed property file here since we don't want any weird preprocessor changes to be applied to the screen/value layout.
		original.forEach((keyObject, valueObject) -> {
			String key = (String) keyObject;
			String value = (String) valueObject;

			// Defining "sliders" multiple times in the properties file will only result in
			// the last definition being used, should be tested if behavior matches OptiFine
			handleWhitespacedListDirective(key, value, "sliders", sliders -> sliderOptions = sliders);
			handlePrefixedWhitespacedListDirective("profile.", key, value, profiles::put);

			if (handleIntDirective(key, value, "screen.columns", columns -> mainScreenColumnCount = columns)) {
				return;
			}

			if (handleAffixedIntDirective("screen.", ".columns", key, value, subScreenColumnCount::put)) {
				return;
			}

			handleWhitespacedListDirective(key, value, "screen", options -> mainScreenOptions = options);
			handlePrefixedWhitespacedListDirective("screen.", key, value, subScreenOptions::put);
		});
	}

	private static void handleBooleanValue(String key, String value, BooleanConsumer handler) {
		if ("true".equals(value)) {
			handler.accept(true);
		} else if ("false".equals(value)) {
			handler.accept(false);
		} else {
			Iris.logger.warn("Unexpected value for boolean key " + key + " in shaders.properties: got " + value + ", but expected either true or false");
		}
	}

	private static void handleBooleanDirective(String key, String value, String expectedKey, Consumer<OptionalBoolean> handler) {
		if (!expectedKey.equals(key)) {
			return;
		}

		if ("true".equals(value)) {
			handler.accept(OptionalBoolean.TRUE);
		} else if ("false".equals(value)) {
			handler.accept(OptionalBoolean.FALSE);
		} else {
			Iris.logger.warn("Unexpected value for boolean key " + key + " in shaders.properties: got " + value + ", but expected either true or false");
		}
	}

	private static boolean handleIntDirective(String key, String value, String expectedKey, Consumer<Integer> handler) {
		if (!expectedKey.equals(key)) {
			return false;
		}

		try {
			int result = Integer.parseInt(value);

			handler.accept(result);
		} catch (NumberFormatException nex) {
			Iris.logger.warn("Unexpected value for integer key " + key + " in shaders.properties: got " + value + ", but expected an integer");
		}

		return true;
	}

	private static boolean handleAffixedIntDirective(String prefix, String suffix, String key, String value, BiConsumer<String, Integer> handler) {
		if (key.startsWith(prefix) && key.endsWith(suffix)) {
			int substrBegin = prefix.length();
			int substrEnd = key.length() - suffix.length();

			if (substrEnd <= substrBegin) {
				return false;
			}

			String affixStrippedKey = key.substring(substrBegin, substrEnd);

			try {
				int result = Integer.parseInt(value);

				handler.accept(affixStrippedKey, result);
			} catch (NumberFormatException nex) {
				Iris.logger.warn("Unexpected value for integer key " + key + " in shaders.properties: got " + value + ", but expected an integer");
			}

			return true;
		}

		return false;
	}

	private static void handlePassDirective(String prefix, String key, String value, Consumer<String> handler) {
		if (key.startsWith(prefix)) {
			String pass = key.substring(prefix.length());

			handler.accept(pass);
		}
	}

	private static void handleProgramEnabledDirective(String prefix, String key, String value, Consumer<String> handler) {
		if (key.startsWith(prefix)) {
			String program = key.substring(prefix.length(), key.indexOf(".", prefix.length()));

			handler.accept(program);
		}
	}

	private static void handleWhitespacedListDirective(String key, String value, String expectedKey, Consumer<List<String>> handler) {
		if (!expectedKey.equals(key)) {
			return;
		}

		String[] elements = value.split(" +");

		handler.accept(Arrays.asList(elements));
	}

	private static void handlePrefixedWhitespacedListDirective(String prefix, String key, String value, BiConsumer<String, List<String>> handler) {
		if (key.startsWith(prefix)) {
			String prefixStrippedKey = key.substring(prefix.length());
			String[] elements = value.split(" +");

			handler.accept(prefixStrippedKey, Arrays.asList(elements));
		}
	}

	private static void handleTwoArgDirective(String prefix, String key, String value, BiConsumer<String, String> handler) {
		if (key.startsWith(prefix)) {
			int endOfPassIndex = key.indexOf(".", prefix.length());
			String stage = key.substring(prefix.length(), endOfPassIndex);
			String sampler = key.substring(endOfPassIndex + 1);

			handler.accept(stage, sampler);
		}
	}

	public static ShaderProperties empty() {
		return new ShaderProperties();
	}

	public CloudSetting getCloudSetting() {
		return cloudSetting;
	}

	public OptionalBoolean getOldHandLight() {
		return oldHandLight;
	}

	public OptionalBoolean getDynamicHandLight() {
		return dynamicHandLight;
	}

	public OptionalBoolean getOldLighting() {
		return oldLighting;
	}

	public OptionalBoolean getShadowTerrain() {
		return shadowTerrain;
	}

	public OptionalBoolean getShadowTranslucent() {
		return shadowTranslucent;
	}

	public OptionalBoolean getShadowEntities() {
		return shadowEntities;
	}

	public OptionalBoolean getShadowPlayer() {
		return shadowPlayer;
	}

	public OptionalBoolean getShadowBlockEntities() {
		return shadowBlockEntities;
	}

	public OptionalBoolean getUnderwaterOverlay() {
		return underwaterOverlay;
	}

	public OptionalBoolean getSun() {
		return sun;
	}

	public OptionalBoolean getMoon() {
		return moon;
	}

	public OptionalBoolean getVignette() {
		return vignette;
	}

	public OptionalBoolean getBackFaceSolid() {
		return backFaceSolid;
	}

	public OptionalBoolean getBackFaceCutout() {
		return backFaceCutout;
	}

	public OptionalBoolean getBackFaceCutoutMipped() {
		return backFaceCutoutMipped;
	}

	public OptionalBoolean getBackFaceTranslucent() {
		return backFaceTranslucent;
	}

	public OptionalBoolean getRainDepth() {
		return rainDepth;
	}

	public OptionalBoolean getBeaconBeamDepth() {
		return beaconBeamDepth;
	}

	public OptionalBoolean getSeparateAo() {
		return separateAo;
	}

	public OptionalBoolean getFrustumCulling() {
		return frustumCulling;
	}

	public OptionalBoolean getShadowCulling() {
		return shadowCulling;
	}

	public OptionalBoolean getParticlesBeforeDeferred() {
		return particlesBeforeDeferred;
	}

	public OptionalBoolean getConcurrentCompute() {
		return concurrentCompute;
	}

	public OptionalBoolean getPrepareBeforeShadow() {
		return prepareBeforeShadow;
	}

	public Object2ObjectMap<String, AlphaTestOverride> getAlphaTestOverrides() {
		return alphaTestOverrides;
	}

	public Object2FloatMap<String> getViewportScaleOverrides() {
		return viewportScaleOverrides;
	}

	public Object2ObjectMap<String, TextureScaleOverride> getTextureScaleOverrides() {
		return textureScaleOverrides;
	}

	public Object2ObjectMap<String, BlendModeOverride> getBlendModeOverrides() {
		return blendModeOverrides;
	}

	public Object2ObjectMap<String, ArrayList<BufferBlendOverride>> getBufferBlendOverrides() {
		return bufferBlendOverrides;
	}

	public EnumMap<TextureStage, Object2ObjectMap<String, String>> getCustomTextures() {
		return customTextures;
	}

	public Optional<String> getNoiseTexturePath() {
		return Optional.ofNullable(noiseTexturePath);
	}

	public Object2ObjectMap<String, String> getConditionallyEnabledPrograms() {
		return conditionallyEnabledPrograms;
	}

	public List<String> getSliderOptions() {
		return sliderOptions;
	}

	public Map<String, List<String>> getProfiles() {
		return profiles;
	}

	public Optional<List<String>> getMainScreenOptions() {
		return Optional.ofNullable(mainScreenOptions);
	}

	public Map<String, List<String>> getSubScreenOptions() {
		return subScreenOptions;
	}

	public Optional<Integer> getMainScreenColumnCount() {
		return Optional.ofNullable(mainScreenColumnCount);
	}

	public Map<String, Integer> getSubScreenColumnCount() {
		return subScreenColumnCount;
	}

	public Object2ObjectMap<String, Object2BooleanMap<String>> getExplicitFlips() {
		return explicitFlips;
	}
}
