package ocean;

// Steuerrichtungen eines Schiffes  

//Alle enums bieten automatisch folgenden Methoden:
//String name() - Liefert der Enum-Wert als String
//int ordinal() - Liefert den Enum-Wert als Zahl
//EnumObj EnumName.valueOf(String name) - wandelt einen String in Enum-Wert um

public enum Rudder {
	Left,		// links,Backbord, portside 
	Center, 	// geradeaus in gleicher Richtung 
	Right,		// rechts, Steuerbord, bowside 
}

