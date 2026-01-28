package ocean;

import java.util.Objects;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class RadarEcho {
	
	private Vec2D sector;
	private Ground ground;
	private int height;
	public static final int INVALID_HEIGHT = Integer.MIN_VALUE;
	
	public RadarEcho(Vec2D sector, Ground ground, int height) {
		super();
		this.sector = sector;
		this.ground = ground;
		this.height = height;
	}

	public RadarEcho(RadarEcho v) {
		this.sector = v.sector;
		this.ground = v.ground;
		this.height = v.height;
	}
	
	public String toString() {
		return "("+ sector+ ","+ground.name()+","+height+")";
	}

	public JSONObject toJson() { 
		JSONObject jo = new JSONObject();
		jo.put("sector", sector.toJson());
		jo.put("ground", ground.name());
		jo.put("height", height);
		return jo;
	}	
	
	public Vec2D getSector() {
		return sector;
	}

	public Ground getGround() {
		return ground;
	}

	public int getHeight() {
		return height;
	}

	public static RadarEcho fromJson(String json) {
		return fromJson(new JSONObject(json));
	}
	
	public static RadarEcho fromJson(JSONObject jo) {
		try {
			Vec2D sec = Vec2D.fromJson(jo.getJSONObject("sector"));
			Ground g = Ground.valueOf(jo.getString("ground"));
			int h = jo.getInt("height");
			RadarEcho rm = new RadarEcho(sec, g, h);
			return rm;
		} catch (Exception e) {
			System.err.println("RadarMeasure.fromJson(jo): invalid data: "+jo.toString(2));
			return null;			
		}

	}
	
	
}
