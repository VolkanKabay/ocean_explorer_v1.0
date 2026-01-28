package ocean;

// Zielrichtungen beim Steueren eines TauchRoboters  

//Alle enums bieten automatisch folgenden Methoden:
//String name() - Liefert der Enum-Wert als String
//int ordinal() - Liefert den Enum-Wert als Zahl
//EnumObj EnumName.valueOf(String name) - wandelt einen String in Enum-Wert um

// Kurs relativ zur Fahrtrichtung

public enum Route {  
	C,		// Center, 
	N, 		// North, 
	NE,		// NorthEast, 
	E,		// East, 
	SE,		// SouthEast, 
	S,		// South, 
	SW,		// SouthWest, 
	W,		// West, 
	NW,		// NorthWest
	UP,		
	DOWN,
	None
}
