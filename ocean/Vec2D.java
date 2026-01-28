package ocean;

import java.util.Objects;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

//Hilfsklasse zur Beschreibung einer 2D-Koordinate bzw. eines 2D-Richtungsvektors
//entsprechende JSON-Darstellung eines Vec2D-Objekts:
//"vec2":[x,y]
public class Vec2D {

	private int x;
	private int y;
	
	private static final Vec2D[] NeighbourOffsets = {new Vec2D(-1,0),new Vec2D(-1,1),new Vec2D(0,1),new Vec2D(1,1),new Vec2D(1,0),new Vec2D(1,-1),new Vec2D(0,-1),new Vec2D(-1,-1) };
	
	public Vec2D() {
		// TODO Auto-generated constructor stub
	}

	public Vec2D(int x, int y) {
		super();
		this.x = x;
		this.y = y;
	}
	
	public Vec2D(Vec2D v) {
		this.x = v.x;
		this.y = v.y;
	}

	public int getX() {
		return x;
	}

	public void setX(int x) {
		this.x = x;
	}

	public int getY() {
		return y;
	}

	public void setY(int y) {
		this.y = y;
	}

	// Addiert zum aktuelle Vektor den Uebergebenen
	// und liefert Referenz auf geaendertes Objekt
	public Vec2D add(Vec2D v ) {
		this.x += v.x;
		this.y += v.y;
		return this;
	}
	
	public Vec2D add(int offset) {
		this.x += offset;
		this.y += offset;
		return this;
	}
	// Addiert zum aktuelle Vektor den Uebergebenen
	// und liefert neues Objekt mit dem Ergebnis
	// Das Ursprungsobjekt bleibt unveraendert
	public Vec2D getSumVec(Vec2D v ) {
		Vec2D value = new Vec2D(this);
		value.x += v.x;
		value.y += v.y;
		return value;
	}

	public Vec2D mul(int factor) {
		this.x *= factor;
		this.y *= factor;
		return this;	
	}
	
	public String toString() {
		return "("+ x+ ","+y+")";
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(x, y);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Vec2D other = (Vec2D) obj;
		return x == other.x && y == other.y;
	}
	public JSONObject toJson() { // String key, JSONObject jo){
		JSONObject jo = new JSONObject();
		jo.put("vec2", toJsonArray());
		return jo;
	}	
	
	public JSONArray toJsonArray() {
		JSONArray vec = new JSONArray();
		vec.put(x);
		vec.put(y);
		return vec; 
	}
	// Umwanldung in 3D-Vektor
	public Vec asVec() {
		return new Vec(x, y, 0);
	}
	// Richtung spiegeln
	public Vec2D invert() {
		return new Vec2D(x * -1,y * -1);
	}
	
	// Liefert ein Array aller 8 angrenzenden Koordinaten 
	// es gibt keine Bereichspruefung
	public Vec2D[] getNeighbours() {
		Vec2D[] neighbours = new Vec2D[8];
		for(int i=0; i<neighbours.length; i++) {
			neighbours[i] = this.getSumVec(NeighbourOffsets[i]);
		}
		return neighbours;
	}
	
	public static Vec2D fromJson(String json) {
		return fromJson(new JSONObject(json));
	}
	
	public static Vec2D fromJson(JSONArray ja) {
		if(ja.length()==2) {
			return new Vec2D(ja.getInt(0), ja.getInt(1));
		} else {
			System.err.println("Vec2D.fromJson(ja): invalid data: "+ja.toString(2));
		}
		return null;
	}
	
	public static Vec2D fromJson(JSONObject jo) {
		try {
			JSONArray jvec = jo.getJSONArray("vec2");
			return new Vec2D(jvec.getInt(0), jvec.getInt(1));
		} catch (JSONException e) {
			System.err.println("Vec.fromJson(jo): invalid data: "+jo.toString(2));
			return null;
		}
	}
}
