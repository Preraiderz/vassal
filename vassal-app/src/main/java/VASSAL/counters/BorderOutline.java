/*
 *
 * Copyright (c) 2023 by Vassalengine.org, Brian Reynolds
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License (LGPL) as published by the Free Software Foundation.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this library; if not, copies are available
 * at http://www.opensource.org.
 */
package VASSAL.counters;

import VASSAL.build.module.documentation.HelpFile;
import VASSAL.command.Command;
import VASSAL.configure.ColorConfigurer;
import VASSAL.configure.IntConfigurer;
import VASSAL.configure.StringConfigurer;
import VASSAL.i18n.Resources;
import VASSAL.i18n.TranslatablePiece;
import VASSAL.tools.SequenceEncoder;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.Objects;

/**
 * Trait to draw a colored border around a piece
 */
public class BorderOutline extends Decorator implements TranslatablePiece {
  public static final String ID = "border;"; // NON-NLS

  private String propertyName;
  private String description;
  private int thickness = 2;
  private Color color = Color.RED;

  private final ColoredBorder border = new ColoredBorder();

  public BorderOutline() {
    this(ID + ";", null); // NON-NLS
  }

  public BorderOutline(String type, GamePiece p) {
    mySetType(type);
    setInner(p);
  }

  @Override
  public void mySetType(String type) {
    final SequenceEncoder.Decoder st = new SequenceEncoder.Decoder(type, ';');
    st.nextToken();
    propertyName = st.nextToken("");
    description  = st.nextToken("");
    thickness    = st.nextInt(2);
    color        = st.nextColor(Color.RED);

    border.setColor(color);
    border.setThickness(thickness);
  }

  @Override
  public void mySetState(String newState) {
  }

  @Override
  public String myGetState() {
    return "";
  }

  @Override
  public String myGetType() {
    final SequenceEncoder se = new SequenceEncoder(';');
    se.append(propertyName).append(description).append(thickness).append(color);
    return ID + se.getValue();
  }

  @Override
  protected KeyCommand[] myGetKeyCommands() {
    return KeyCommand.NONE;
  }

  @Override
  public Command myKeyEvent(javax.swing.KeyStroke stroke) {
    return null;
  }

  @Override
  public Shape getShape() {
    return piece.getShape();
  }

  @Override
  public Rectangle boundingBox() {
    final Rectangle r = piece.boundingBox();
    r.add(border.boundingBox(this));
    return r;
  }

  @Override
  public String getName() {
    return piece.getName();
  }

  @Override
  public void draw(Graphics g, int x, int y, Component obs, double zoom) {
    piece.draw(g, x, y, obs, zoom);

    if ((propertyName != null) && !propertyName.isEmpty()) {
      final Object propValue = Decorator.getOutermost(this).getProperty(propertyName);
      if (propValue == null) {
        return;
      }
      else if (propValue instanceof String) {
        final String string = (String)propValue;
        if ("".equals(string) || "false".equals(string) || "0".equals(string)) { //NON-NLS
          return;
        }
      }
      else if (propValue instanceof Boolean) {
        if (!((Boolean)propValue)) return;
      }
      else if (propValue instanceof Integer) {
        if (((Integer)propValue) == 0) return;
      }
    }
    border.draw(this, g, x, y, obs, zoom);
  }

  @Override
  public String getDescription() {
    return buildDescription("Editor.BorderOutline.trait_description", propertyName, description);
  }

  @Override
  public String getBaseDescription() {
    return Resources.getString("Editor.BorderOutline.trait_description");
  }

  @Override
  public String getDescriptionField() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  @Override
  public HelpFile getHelpFile() {
    return HelpFile.getReferenceManualPage("BorderOutline.html"); // NON-NLS
  }

  @Override
  public PieceEditor getEditor() {
    return new Ed(this);
  }

  @Override
  public boolean testEquals(Object o) {
    if (! (o instanceof BorderOutline)) return false;
    final BorderOutline c = (BorderOutline) o;
    if (! Objects.equals(color, c.color)) return false;
    if (! Objects.equals(propertyName, c.propertyName)) return false;
    return Objects.equals(thickness, c.thickness);
  }

  private static class Ed implements PieceEditor {
    private final StringConfigurer propertyInput;
    private final StringConfigurer descInput;
    private final IntConfigurer thicknessConfig;
    private final ColorConfigurer colorConfig;
    private final TraitConfigPanel box;

    private Ed(BorderOutline p) {

      box = new TraitConfigPanel();

      descInput = new StringConfigurer(p.description);
      descInput.setHintKey("Editor.description_hint");
      box.add("Editor.description_label", descInput);

      propertyInput = new StringConfigurer(p.propertyName);
      box.add("Editor.BorderOutline.property_name", propertyInput);

      colorConfig = new ColorConfigurer(p.color);
      box.add("Editor.BorderOutline.color", colorConfig);

      thicknessConfig = new IntConfigurer(p.thickness);
      box.add("Editor.BorderOutline.thickness", thicknessConfig);
    }

    @Override
    public Component getControls() {
      return box;
    }

    @Override
    public String getType() {
      final SequenceEncoder se = new SequenceEncoder(';');
      se.append(propertyInput.getValueString()).append(descInput.getValueString()).append(thicknessConfig.getValueString()).append(colorConfig.getValueString());
      return ID + se.getValue();
    }

    @Override
    public String getState() {
      return "false"; // NON-NLS
    }
  }
}
