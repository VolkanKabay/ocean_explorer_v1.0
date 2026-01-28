package ocean;

import java.util.Objects;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

// Hilfsklasse zur Beschreibung einer 3D-Koordinate bzw. eines 3D-Richtungsvektors
// entsprechende JSON-Darstellung eines Vec-Objekts:
// "vec":[x,y,z]
public class Vec {
	
	public static final Vec NullVec = new Vec(0,0,0);
	
	private int x;
	private int y;
	private int z;
	 
	public Vec() {
		
	}
	
	public Vec(int x, int y, int z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public Vec(Vec v) {
		this.x = v.x;
		this.y = v.y;
		this.z = v.z;
	}
	
	public String toString() {
		return "("+ x+ ","+y+","+z+")";
	}
	
	public Vec add(Vec v ) {
		this.x += v.x;
		this.y += v.y;
		this.z += v.z;
		return this;
	}

	public Vec mul(int factor ) {
		this.x *= factor;
		this.y *= factor;
		this.z *= factor;
		return this;
	}

	public Vec getDelta(Vec v) {
		return new Vec (x-v.x, y-v.y, z-v.z);
	}
	
	public int getX() {
		return x;
	}
	public int getY() {
		return y;
	}
	public int getZ() {
		return z;
	}

	public void setX(int x) {
		this.x = x;
	}

	public void setY(int y) {
		this.y = y;
	}

	public void setZ(int z) {
		this.z = z;
	}

	public Vec2D reduce() {
		return new Vec2D(x,y);
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(x, y, z);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Vec other = (Vec) obj;
		return x == other.x && y == other.y && z == other.z;
	}
	
	public JSONObject toJson() { // String key, JSONObject jo){
		JSONObject jo = new JSONObject();
		jo.put("vec", toJsonArray());
		return jo;
	}	
	
	public JSONArray toJsonArray() {
		JSONArray vec = new JSONArray();
		vec.put(x);
		vec.put(y);
		vec.put(z);
		return vec; 
	}
	
	public static Vec fromJson(String json) {
		return fromJson(new JSONObject(json));
	}
	
	public static Vec fromJson(JSONArray ja) {
		if(ja.length()==3) {
			return new Vec(ja.getInt(0), ja.getInt(1), ja.getInt(2));
		} else {
			System.err.println("Vec.fromJson(ja): invalid data: "+ja.toString(2));
		}
		return null;
	}
	
	public static Vec fromJson(JSONObject jo) {
		try {
			JSONArray jvec = jo.getJSONArray("vec");
			return new Vec(jvec.getInt(0), jvec.getInt(1), jvec.getInt(2));
		} catch (JSONException e) {
			System.err.println("Vec.fromJson(jo): invalid data: "+jo.toString(2));
			return null;
		}
	}

}
