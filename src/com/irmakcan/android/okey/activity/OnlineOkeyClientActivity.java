package com.irmakcan.android.okey.activity;

import java.util.HashMap;
import java.util.Map;

import org.andengine.engine.camera.Camera;
import org.andengine.engine.options.EngineOptions;
import org.andengine.engine.options.ScreenOrientation;
import org.andengine.engine.options.resolutionpolicy.RatioResolutionPolicy;
import org.andengine.entity.primitive.Rectangle;
import org.andengine.entity.scene.Scene;
import org.andengine.entity.scene.background.Background;
import org.andengine.entity.util.FPSLogger;
import org.andengine.opengl.font.Font;
import org.andengine.opengl.font.FontFactory;
import org.andengine.opengl.texture.TextureOptions;
import org.andengine.opengl.texture.atlas.bitmap.BitmapTextureAtlas;
import org.andengine.opengl.texture.atlas.bitmap.BitmapTextureAtlasTextureRegionFactory;
import org.andengine.opengl.texture.region.ITextureRegion;
import org.andengine.ui.activity.BaseGameActivity;
import org.json.JSONObject;

import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.irmakcan.android.okey.gson.BaseResponse;
import com.irmakcan.android.okey.gson.DrawTileResponse;
import com.irmakcan.android.okey.gson.ErrorResponse;
import com.irmakcan.android.okey.gson.GameStartResponse;
import com.irmakcan.android.okey.gson.ModelDeserializer;
import com.irmakcan.android.okey.gson.ThrowTileResponse;
import com.irmakcan.android.okey.gson.WonResponse;
import com.irmakcan.android.okey.gui.BlankTileSprite;
import com.irmakcan.android.okey.gui.Board;
import com.irmakcan.android.okey.gui.Constants;
import com.irmakcan.android.okey.gui.CornerTileStackRectangle;
import com.irmakcan.android.okey.gui.TileSprite;
import com.irmakcan.android.okey.model.GameInformation;
import com.irmakcan.android.okey.model.Player;
import com.irmakcan.android.okey.model.Position;
import com.irmakcan.android.okey.model.TableCorner;
import com.irmakcan.android.okey.model.TableManager;
import com.irmakcan.android.okey.model.Tile;
import com.irmakcan.android.okey.websocket.WebSocketProvider;

import de.roderick.weberknecht.WebSocket;
import de.roderick.weberknecht.WebSocketEventHandler;
import de.roderick.weberknecht.WebSocketMessage;

public class OnlineOkeyClientActivity extends BaseGameActivity {
	// ===========================================================
	// Constants
	// ===========================================================
	private static final String LOG_TAG = "OnlineOkeyClientActivity";

	private static final int CAMERA_WIDTH = 800;
	private static final int CAMERA_HEIGHT = 480;

	private static final int TILE_WIDTH = 56;
	private static final int TILE_HEIGHT = 84;

	private static final int CORNER_X_MARGIN = 30;
	private static final int CORNER_Y_MARGIN = 20;
	private static final Point[] CORNER_POINTS = new Point[] { 
		new Point((CAMERA_WIDTH - (TILE_WIDTH + CORNER_X_MARGIN)), CAMERA_HEIGHT - (((int)Constants.FRAGMENT_HEIGHT*2) + CORNER_Y_MARGIN + TILE_HEIGHT)), 
		new Point((CAMERA_WIDTH - (TILE_WIDTH + CORNER_X_MARGIN)), CORNER_Y_MARGIN), 
		new Point(CORNER_X_MARGIN, CORNER_Y_MARGIN), 
		new Point(CORNER_X_MARGIN, CAMERA_HEIGHT - (((int)Constants.FRAGMENT_HEIGHT*2) + CORNER_Y_MARGIN + TILE_HEIGHT)) };
	// ===========================================================
	// Fields
	// ===========================================================

	private GameInformation mGameInformation;

	private TableManager mTableManager;



	private ITextureRegion mTileTextureRegion;
	private ITextureRegion mBoardWoodTextureRegion;

	private Font mTileFont;

	private Board mBoard;
	private Map<TableCorner, CornerTileStackRectangle> mCornerStacks;
	private Rectangle mCenterArea;
	
	private Scene mScene;


	// ===========================================================
	// Constructors
	// ===========================================================

	// ===========================================================
	// Getter & Setter
	// ===========================================================

	// ===========================================================
	// Methods for/from SuperClass/Interfaces
	// ===========================================================
	@Override
	protected void onCreate(Bundle pSavedInstanceState) {
		super.onCreate(pSavedInstanceState);
		Bundle b = this.getIntent().getExtras();
		if(b==null){
			this.finish();
		}else{
			this.mGameInformation = (GameInformation) b.getSerializable("game_information");
		}
		Log.v(LOG_TAG, "Table Name: " + this.mGameInformation.getTableName());
	}

	@Override
	public EngineOptions onCreateEngineOptions() {
		final Camera camera = new Camera(0, 0, CAMERA_WIDTH, CAMERA_HEIGHT);
		return new EngineOptions(true, ScreenOrientation.LANDSCAPE_FIXED, new RatioResolutionPolicy(CAMERA_WIDTH, CAMERA_HEIGHT), camera);
	}

	@Override
	public void onCreateResources(OnCreateResourcesCallback pOnCreateResourcesCallback) {
		BitmapTextureAtlasTextureRegionFactory.setAssetBasePath("gfx/");
		BitmapTextureAtlas bitmapTextureAtlas;
		// Tile
		bitmapTextureAtlas = new BitmapTextureAtlas(this.getTextureManager(), TILE_WIDTH, TILE_HEIGHT, TextureOptions.BILINEAR);
		this.mTileTextureRegion = BitmapTextureAtlasTextureRegionFactory.createFromAsset(bitmapTextureAtlas, this, "tile.png", 0, 0);
		bitmapTextureAtlas.load();

		// Board
		bitmapTextureAtlas = new BitmapTextureAtlas(this.getTextureManager(), 96, 144, TextureOptions.BILINEAR);
		this.mBoardWoodTextureRegion = BitmapTextureAtlasTextureRegionFactory.createFromAsset(bitmapTextureAtlas, this, "board_wood.png", 0, 0);
		bitmapTextureAtlas.load();

		// Load Fonts
		this.mTileFont = FontFactory.create(this.getFontManager(), this.getTextureManager(), 256, 256, Typeface.create(Typeface.DEFAULT, Typeface.BOLD), 48, Color.WHITE);
		this.mTileFont.load();

		pOnCreateResourcesCallback.onCreateResourcesFinished();
	}

	@Override
	public void onCreateScene(OnCreateSceneCallback pOnCreateSceneCallback) {
		this.mEngine.registerUpdateHandler(new FPSLogger());

		mScene = new Scene();
		mScene.setBackground(new Background(0.09804f, 0.6274f, 0.8784f));

		// Create board
		this.mBoard = new Board(0, 0, mBoardWoodTextureRegion, this.getVertexBufferObjectManager());
		mBoard.setPosition((CAMERA_WIDTH/2)-(mBoard.getWidth()/2), CAMERA_HEIGHT-mBoard.getHeight());
		mScene.attachChild(mBoard);

		// Create corners
		this.mCornerStacks = new HashMap<TableCorner, CornerTileStackRectangle>();
		Position position = Player.getPlayer().getPosition();

		for(int i=0;i < 4;i++){
			TableCorner corner = TableCorner.nextCornerFromPosition(position);
			Log.v(LOG_TAG, corner.toString());
			final Point point = CORNER_POINTS[i];
			final CornerTileStackRectangle cornerStack = new CornerTileStackRectangle(point.x, point.y, TILE_WIDTH, TILE_HEIGHT, this.getVertexBufferObjectManager(), corner);
			mScene.attachChild(cornerStack);
			this.mCornerStacks.put(corner, cornerStack);
			position = corner.nextPosition();
		}
		// Create center throwing area
		this.mCenterArea = new Rectangle(2*CORNER_X_MARGIN + Constants.TILE_WIDTH, CORNER_Y_MARGIN,
				CAMERA_WIDTH - (2*(2*CORNER_X_MARGIN + Constants.TILE_WIDTH)), CAMERA_HEIGHT - (mBoard.getHeight() + 2*CORNER_Y_MARGIN), 
				this.getVertexBufferObjectManager());
		this.mCenterArea.setAlpha(0f);
		mScene.attachChild(this.mCenterArea);

		mScene.setTouchAreaBindingOnActionDownEnabled(true);
		mScene.setTouchAreaBindingOnActionMoveEnabled(true);

		pOnCreateSceneCallback.onCreateSceneFinished(mScene);
	}

	@Override
	public void onPopulateScene(Scene pScene, OnPopulateSceneCallback pOnPopulateSceneCallback)	throws Exception {

		pOnPopulateSceneCallback.onPopulateSceneFinished();
	}

	@Override
	public synchronized void onGameCreated() {
		super.onGameCreated();

		this.mTableManager = new TableManager(Player.getPlayer().getPosition(), mBoard, this.mCornerStacks, this.mCenterArea);
		WebSocket webSocket = WebSocketProvider.getWebSocket();
		webSocket.setEventHandler(mWebSocketEventHandler);

		try {
			JSONObject json = new JSONObject().put("action", "ready");
			webSocket.send(json.toString());
		} catch (Exception e) {
			// TODO Handle
			e.printStackTrace();
		}

	}
	// ===========================================================
	// Methods
	// ===========================================================

	private TileSprite createNewTileSprite(final Tile pTile) {
		return new TileSprite(0, 0, this.mTileTextureRegion, this.getVertexBufferObjectManager(), pTile , this.mTileFont, this.mTableManager);
	}

	// ===========================================================
	// Inner and Anonymous Classes
	// ===========================================================

	private WebSocketEventHandler mWebSocketEventHandler = new WebSocketEventHandler() {
		@Override
		public void onOpen() {
			Log.v(LOG_TAG, "OkeyGame WebSocket connected");
			throw new IllegalAccessError("Should not call onOpen in OkeyGame");
		}
		@Override
		public void onMessage(WebSocketMessage message) {
			Log.v(LOG_TAG, "OkeyGame Message received: " + message.getText());
			Gson gson = new Gson();
			BaseResponse baseResponse = gson.fromJson(message.getText(), BaseResponse.class);
			String status = baseResponse.getStatus();
			if(status.equals("error")){
				final ErrorResponse errorResponse = gson.fromJson(message.getText(), ErrorResponse.class);
				OnlineOkeyClientActivity.this.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						Toast.makeText(OnlineOkeyClientActivity.this.getApplicationContext(), errorResponse.getMessage(), Toast.LENGTH_SHORT).show();
						OnlineOkeyClientActivity.this.mTableManager.cancelPendingOperation();
					}
				});
			} else if(status.equals("throw_tile")){
				// {"status":"throw_tile","turn":"east","tile":"2:2"}
				gson = new GsonBuilder().registerTypeAdapter(Position.class, new ModelDeserializer.PositionDeserializer())
						.registerTypeAdapter(Tile.class, new ModelDeserializer.TileDeserializer())
						.create();
				final ThrowTileResponse throwTileResponse = gson.fromJson(message.getText(), ThrowTileResponse.class);
				Log.v(LOG_TAG, throwTileResponse.getStatus() + throwTileResponse.getTile().toString() + throwTileResponse.getTurn().toString());
				final TableCorner prevCorner = TableCorner.previousCornerFromPosition(throwTileResponse.getTurn());


				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						if(TableCorner.nextCornerFromPosition(mTableManager.getUserPosition()) == prevCorner){ // Tile thrown by this user
							mTableManager.setTurn(throwTileResponse.getTurn());
							mTableManager.pendingOperationSuccess(mTableManager.getCornerStack(prevCorner));
						} else {
							TileSprite tileSprite = createNewTileSprite(throwTileResponse.getTile());
							if(throwTileResponse.getTurn() == mTableManager.getUserPosition()){
								mScene.registerTouchArea(tileSprite);
								tileSprite.enableTouch();
							}
							mScene.attachChild(tileSprite);
							mTableManager.getCornerStack(prevCorner).push(tileSprite);
							mTableManager.setTurn(throwTileResponse.getTurn());
						}
					}
				});

			} else if(status.equals("draw_tile")){
				// {"status":"draw_tile","tile":"8:0","turn":"east","center_count":47}
				gson = new GsonBuilder().registerTypeAdapter(Position.class, new ModelDeserializer.PositionDeserializer())
						.registerTypeAdapter(Tile.class, new ModelDeserializer.TileDeserializer())
						.create();
				final DrawTileResponse drawTileResponse = gson.fromJson(message.getText(), DrawTileResponse.class);
				Log.v(LOG_TAG, drawTileResponse.getStatus() + drawTileResponse.getTurn().toString() + drawTileResponse.getCenterCount());
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						if(mTableManager.getUserPosition() == drawTileResponse.getTurn()){ // Drawn by user (center or corner)
							if(mTableManager.getCenterCount() == drawTileResponse.getCenterCount()){
								mTableManager.pendingOperationSuccess(mTableManager.getPreviousCornerStack());
							}else{
								TileSprite tileSprite = createNewTileSprite(drawTileResponse.getTile());
								mScene.registerTouchArea(tileSprite);
								tileSprite.enableTouch();
								mScene.attachChild(tileSprite);
								mTableManager.pendingOperationSuccess(tileSprite);
							}
						} else {
							if(mTableManager.getCenterCount() == drawTileResponse.getCenterCount()){ // Drawn from corner
								CornerTileStackRectangle tileStack = mTableManager.getCornerStack(TableCorner.previousCornerFromPosition(drawTileResponse.getTurn()));
								final TileSprite tileSprite = tileStack.pop();
								tileSprite.dispose();
								//mScene.unregisterTouchArea(tileSprite); TODO test
								runOnUpdateThread(new Runnable() {
									@Override
									public void run() {
										tileSprite.detachSelf();
									}
								});
							} else { // Drawn from center
								// TODO
							}
						}
						mTableManager.setCenterCount(drawTileResponse.getCenterCount());
					}
				});

			} else if(status.equals("game_start")){
				// {"status":"game_start","turn":"south","center_count":48,"hand":["4:0","7:3",...,"6:2"],"indicator":"4:0"}
				gson = new GsonBuilder().registerTypeAdapter(Position.class, new ModelDeserializer.PositionDeserializer())
						.registerTypeAdapter(Tile.class, new ModelDeserializer.TileDeserializer())
						.create();
				final GameStartResponse gameStartResponse = gson.fromJson(message.getText(), GameStartResponse.class);
				Log.v(LOG_TAG, gameStartResponse.getStatus() + gameStartResponse.getIndicator().toString() + 
						gameStartResponse.getTurn().toString() + gameStartResponse.getCenterCount() + gameStartResponse.getUserHand());
				OnlineOkeyClientActivity.this.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						TileSprite indicatorSprite = createNewTileSprite(gameStartResponse.getIndicator());
						indicatorSprite.setPosition(CAMERA_WIDTH/2 - (indicatorSprite.getWidth() + Constants.TILE_PADDING_X), 
								(CAMERA_HEIGHT - mBoard.getHeight())/2 - indicatorSprite.getHeight()/2);
						mScene.attachChild(indicatorSprite);
						// CenterTiles TODO
						BlankTileSprite blankTileSprite = new BlankTileSprite(
								CAMERA_WIDTH/2 + Constants.TILE_PADDING_X, 
								(CAMERA_HEIGHT - mBoard.getHeight())/2 - indicatorSprite.getHeight()/2, 
								mTileTextureRegion, getVertexBufferObjectManager(), mTableManager);
						mScene.registerTouchArea(blankTileSprite);
						blankTileSprite.enableTouch();
						mScene.attachChild(blankTileSprite);

						for(Tile tile : gameStartResponse.getUserHand()){
							TileSprite ts = createNewTileSprite(tile);
							mScene.registerTouchArea(ts);
							ts.enableTouch();
							mScene.attachChild(ts);
							mBoard.addChild(ts);
						}
						mTableManager.setTurn(gameStartResponse.getTurn());
						mTableManager.setCenterCount(gameStartResponse.getCenterCount());
					}
				});
			}else if(status.equals("user_won")){
				//{ "status":"user_won", "turn":user.position, "username":user.username, hand:[[],[]] }
				// Show hand
				gson = new GsonBuilder().registerTypeAdapter(Position.class, new ModelDeserializer.PositionDeserializer())
						.registerTypeAdapter(Tile.class, new ModelDeserializer.TileDeserializer())
						.create();
				final WonResponse wonResponse = gson.fromJson(message.getText(), WonResponse.class);
				Log.v(LOG_TAG, wonResponse.getStatus());
				Log.v(LOG_TAG, wonResponse.getTurn().toString());
				Log.v(LOG_TAG, wonResponse.getUsername().toString());
				for(Tile[] group : wonResponse.getHand()){
					for(Tile t : group){
						Log.v(LOG_TAG, t.toString());
					}
				}
				
			}
		}
		@Override
		public void onClose() {
			Log.v(LOG_TAG, "OkeyGame WebSocket disconnected");
		}
	};
}