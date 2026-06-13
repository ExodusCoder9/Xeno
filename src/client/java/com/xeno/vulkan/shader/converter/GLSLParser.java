package com.xeno.vulkan.shader.converter;

/*
 * Original Codebase: Copyright XCollateral (VulkanMod)
 * Refactored Codebase: Copyright ExodusCoder9 (Xeno)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * Refactored, Renamed and Optimized by ExodusCoder9.
 */


import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import com.xeno.Initializer;
import com.xeno.vulkan.shader.descriptor.ImageDescriptor;
import com.xeno.vulkan.shader.descriptor.UBO;
import com.xeno.vulkan.shader.layout.AlignedStruct;
import com.xeno.vulkan.shader.layout.Uniform;

public class GLSLParser {
   private Lexer lexer;
   private List<Token> tokens;
   private int currentTokenIdx;
   private Token currentToken;
   private GLSLParser.Stage stage;
   GLSLParser.PreprocessorState preprocessorState = GLSLParser.PreprocessorState.DEFAULT;
   GLSLParser.State state = GLSLParser.State.DEFAULT;
   LinkedList<GLSLParser.Node> vsStream = new LinkedList<>();
   LinkedList<GLSLParser.Node> fsStream = new LinkedList<>();
   Set<String> defines = new HashSet<>();
   int currentUniformLocation = 0;
   List<UniformBlock> uniformBlocks = new ArrayList<>();
   Map<String, UniformBlock> uniformBlockMap = new HashMap<>();
   List<Sampler> samplers = new ArrayList<>();
   Map<String, Sampler> samplerMap = new HashMap<>();
   List<UniformBlock.Field> legacyUniforms = new ArrayList<>();
   VertexFormat vertexFormat;
   int currentInAtt = 0;
   int currentOutAtt = 0;
   ArrayList<Attribute> vertInAttributes = new ArrayList<>();
   ArrayList<Attribute> vertOutAttributes = new ArrayList<>();
   ArrayList<Attribute> fragInAttributes = new ArrayList<>();
   ArrayList<Attribute> fragOutAttributes = new ArrayList<>();

   public GLSLParser() {
   }

   public void setVertexFormat(VertexFormat vertexFormat) {
      this.vertexFormat = vertexFormat;
   }

   public void parse(Lexer lexer, GLSLParser.Stage stage) {
      this.stage = stage;
      this.lexer = lexer;
      this.tokens = this.lexer.tokenize();
      this.currentTokenIdx = 0;
      this.currentInAtt = 0;
      this.currentOutAtt = 0;
      this.advanceToken();
      this.parseVersion();

      while (this.currentToken.type != Token.TokenType.EOF) {
         switch (this.currentToken.type) {
            case PREPROCESSOR:
               this.parsePreprocessor();
            default:
               if (this.preprocessorState != GLSLParser.PreprocessorState.IGNORE) {
                  label59:
                  switch (this.currentToken.type) {
                     case PREPROCESSOR:
                        this.parsePreprocessor();
                        break;
                     case COMMENT:
                     default:
                        this.appendToken(this.currentToken);
                        break;
                     case IDENTIFIER:
                        switch (this.currentToken.value) {
                           case "layout":
                              this.parseUniformBlock();
                              break label59;
                           case "uniform":
                              this.parseUniform();
                              break label59;
                           case "in":
                           case "out":
                              this.parseAttribute();
                              break label59;
                           default:
                              this.appendToken(this.currentToken);
                              break label59;
                        }
                     case OPERATOR:
                        this.appendToken(this.currentToken);
                  }
               } else {
                  this.appendToken(this.currentToken);
               }

               this.advanceToken();
               break;
            case COMMENT:
               this.appendToken(this.currentToken);
               this.advanceToken();
         }
      }

      if (this.stage == GLSLParser.Stage.VERTEX) {
         for (Attribute attribute : this.vertInAttributes) {
            if (attribute.location == -1 && this.vertexFormat != null) {
               List<String> attributeNames = this.vertexFormat.getElementAttributeNames();
               int attributeLocation = attributeNames.indexOf(attribute.id);
               if (attributeLocation == -1) {
                  Initializer.LOGGER.error("Element %s not found in elements %s".formatted(attribute.id, attributeNames));
               } else {
                  attribute.setLocation(attributeLocation);
                  this.currentInAtt++;
               }
            }
         }

         for (Attribute attribute : this.vertInAttributes) {
            if (attribute.location == -1) {
               int attributeLocation = this.currentInAtt++;
               attribute.setLocation(attributeLocation);
            }
         }
      }
   }

   private void parseVersion() {
      if (this.currentToken.type != Token.TokenType.PREPROCESSOR) {
         throw new IllegalStateException("First glsl line must contain #version");
      }

      this.advanceToken();
      if (!this.currentToken.value.startsWith("version")) {
         throw new IllegalStateException("First glsl line must contain #version");
      }

      this.advanceToken();

      while (!this.currentToken.value.contains("\n")) {
         this.advanceToken();
      }

      this.advanceToken();
      this.appendToken(new Token(Token.TokenType.PREPROCESSOR, "#version 450\n"));
   }

   private void parsePreprocessor() {
      int startTokenIdx = this.currentTokenIdx - 1;
      boolean appendTokens = true;
      this.advanceToken(true);
      switch (this.currentToken.value) {
         case "define":
            this.advanceToken(true);
            this.defines.add(this.currentToken.value);
            break;
         case "ifdef":
            this.advanceToken(true);
            if (!this.defines.contains(this.currentToken.value)) {
               this.preprocessorState = GLSLParser.PreprocessorState.IGNORE;
            }
            break;
         case "else":
            if (this.preprocessorState != GLSLParser.PreprocessorState.IGNORE) {
               this.preprocessorState = GLSLParser.PreprocessorState.IGNORE;
            } else {
               this.preprocessorState = GLSLParser.PreprocessorState.DEFAULT;
            }
            break;
         case "endif":
            this.preprocessorState = GLSLParser.PreprocessorState.DEFAULT;
            break;
         case "line":
            appendTokens = false;
      }

      this.currentTokenIdx = startTokenIdx;
      this.currentTokenIdx++;
      this.currentToken = this.tokens.get(startTokenIdx);

      do {
         if (appendTokens) {
            this.appendToken(new Token(Token.TokenType.PREPROCESSOR, this.currentToken.value));
         }

         this.advanceToken(false);
      } while (!this.currentToken.value.contains("\n"));
   }

   private void parseUniform() {
      this.advanceToken(true);
      if (this.currentToken.type != Token.TokenType.IDENTIFIER) {
         throw new IllegalStateException();
      }

      switch (this.currentToken.value) {
         case "sampler2D":
            this.parseSampler(Sampler.Type.SAMPLER_2D);
            break;
         case "samplerCube":
            this.parseSampler(Sampler.Type.SAMPLER_CUBE);
            break;
         case "isamplerBuffer":
            this.parseSampler(Sampler.Type.I_SAMPLER_BUFFER);
            break;
         default:
            this.parseStorageUniform();
      }
   }

   private void parseStorageUniform() {
      UniformBlock.Field field = this.parseUniformField();
      this.legacyUniforms.add(field);
   }

   private void parseSampler(Sampler.Type type) {
      this.advanceToken(true);
      if (this.currentToken.type != Token.TokenType.IDENTIFIER) {
         throw new IllegalStateException();
      }

      String name = this.currentToken.value;
      this.advanceToken(true);
      if (this.currentToken.type != Token.TokenType.SEMICOLON) {
         throw new IllegalStateException();
      }

      Token next = this.tokens.get(this.currentTokenIdx);
      if (next.type == Token.TokenType.SPACING) {
         if (Objects.equals(next.value, "\n")) {
            this.currentTokenIdx++;
         } else {
            int i = next.value.indexOf("\n");
            if (i >= 0) {
               next.value = next.value.substring(i + 1);
            }
         }
      }

      Sampler sampler = new Sampler(type, name);
      if (this.samplerMap.get(name) != null) {
         sampler = this.samplerMap.get(name);
      } else {
         this.samplerMap.put(name, sampler);
         this.samplers.add(sampler);
      }

      this.appendNode(sampler);
   }

   private void parseUniformBlock() {
      this.state = GLSLParser.State.LAYOUT;
      this.advanceToken(true);
      if (this.currentToken.type != Token.TokenType.LEFT_PARENTHESIS) {
         throw new IllegalStateException();
      }

      do {
         this.advanceToken(true);
      } while (this.currentToken.type != Token.TokenType.RIGHT_PARENTHESIS);

      this.advanceToken(true);
      if (!Objects.equals(this.currentToken.value, "uniform")) {
         throw new IllegalStateException();
      }

      this.advanceToken(true);
      String name = this.currentToken.value;
      UniformBlock ub = new UniformBlock(name);
      this.advanceToken(true);
      if (this.currentToken.type != Token.TokenType.LEFT_BRACE) {
         throw new IllegalStateException();
      }

      this.advanceToken(true);

      while (this.currentToken.type != Token.TokenType.RIGHT_BRACE) {
         UniformBlock.Field field = this.parseUniformField();
         ub.addField(field);
         this.advanceToken(true);
      }

      this.advanceToken(true);
      switch (this.currentToken.type) {
         case IDENTIFIER:
            ub.setAlias(this.currentToken.value);
            this.advanceToken(true);
            if (this.currentToken.type != Token.TokenType.SEMICOLON) {
               throw new IllegalStateException();
            }
         case SEMICOLON:
            Token next = this.tokens.get(this.currentTokenIdx);
            if (next.type == Token.TokenType.SPACING) {
               if (Objects.equals(next.value, "\n")) {
                  this.currentTokenIdx++;
               } else {
                  int i = next.value.indexOf("\n");
                  if (i >= 0) {
                     next.value = next.value.substring(i + 1);
                  }
               }
            }

            if (this.uniformBlockMap.get(ub.name) != null) {
               ub = this.uniformBlockMap.get(ub.name);
            } else {
               this.uniformBlockMap.put(ub.name, ub);
               this.uniformBlocks.add(ub);
            }

            this.appendNode(ub);
            return;
         default:
            throw new IllegalStateException();
      }
   }

   private UniformBlock.Field parseUniformField() {
      if (this.currentToken.type != Token.TokenType.IDENTIFIER) {
         throw new IllegalStateException();
      } else {
         String fieldType = this.currentToken.value;
         this.advanceToken(true);
         if (this.currentToken.type != Token.TokenType.IDENTIFIER) {
            throw new IllegalStateException();
         } else {
            String fieldName = this.currentToken.value;
            this.advanceToken(true);
            if (this.currentToken.type != Token.TokenType.SEMICOLON) {
               throw new IllegalStateException();
            } else {
               return new UniformBlock.Field(fieldType, fieldName);
            }
         }
      }
   }

   private void parseAttribute() {
      this.state = GLSLParser.State.ATTRIBUTE;
      GLSLParser.TokenNode prevNode = this.prevNode(true);
      if (prevNode == null || !prevNode.type.equals(Token.TokenType.LEFT_PARENTHESIS.name()) && !prevNode.type.equals(Token.TokenType.COMMA.name())) {
         String ioType = this.currentToken.value;
         this.advanceToken(true);
         if (this.currentToken.type != Token.TokenType.IDENTIFIER) {
            throw new IllegalStateException();
         }

         String type = this.currentToken.value;
         this.advanceToken(true);
         if (this.currentToken.type != Token.TokenType.IDENTIFIER) {
            throw new IllegalStateException();
         }

         String id = this.currentToken.value;
         this.advanceToken(true);
         if (this.currentToken.type != Token.TokenType.SEMICOLON) {
            throw new IllegalStateException();
         }

         Token next = this.tokens.get(this.currentTokenIdx);
         if (next.type == Token.TokenType.SPACING) {
            if (Objects.equals(next.value, "\n")) {
               this.currentTokenIdx++;
            } else {
               int i = next.value.indexOf("\n");
               if (i >= 0) {
                  next.value = next.value.substring(i + 1);
               }
            }
         }

         Attribute attribute;
         attribute = new Attribute(ioType, type, id);
         label61:
         switch (this.stage) {
            case VERTEX:
               switch (attribute.ioType) {
                  case "in":
                     attribute.setLocation(-1);
                     this.vertInAttributes.add(attribute);
                     break label61;
                  case "out":
                     attribute.setLocation(this.currentOutAtt++);
                     this.vertOutAttributes.add(attribute);
                     break label61;
                  default:
                     throw new IllegalStateException();
               }
            case FRAGMENT:
               switch (attribute.ioType) {
                  case "in":
                     Attribute vertAttribute = this.getVertAttribute(attribute);
                     if (vertAttribute == null) {
                        return;
                     }

                     attribute.setLocation(vertAttribute.location);
                     this.fragInAttributes.add(attribute);
                     break;
                  case "out":
                     if (this.currentOutAtt > 0) {
                        throw new UnsupportedOperationException("Multiple outputs not currently supported.");
                     }

                     attribute.setLocation(this.currentOutAtt++);
                     this.fragOutAttributes.add(attribute);
                     break;
                  default:
                     throw new IllegalStateException();
               }
         }

         this.appendNode(attribute);
      }
   }

   private Attribute getVertAttribute(Attribute attribute) {
      Attribute vertAttribute = null;

      for (Attribute attribute1 : this.vertOutAttributes) {
         if (Objects.equals(attribute1.id, attribute.id)) {
            vertAttribute = attribute1;
         }
      }

      if (vertAttribute == null) {
      }

      return vertAttribute;
   }

   private void advanceToken() {
      this.advanceToken(false);
   }

   private void advanceToken(boolean skipSpace) {
      this.currentToken = this.tokens.get(this.currentTokenIdx++);

      while (skipSpace && this.currentToken.type == Token.TokenType.SPACING) {
         this.currentToken = this.tokens.get(this.currentTokenIdx++);
      }
   }

   private Token prevToken(boolean skipSpace) {
      int tokenIdx = this.currentTokenIdx - 1;
      if (tokenIdx == 0) {
         return null;
      }

      Token token = this.tokens.get(--tokenIdx);

      while (
         skipSpace
            && tokenIdx != 0
            && (token.type == Token.TokenType.SPACING || token.type == Token.TokenType.PREPROCESSOR || token.type == Token.TokenType.COMMENT)
      ) {
         token = this.tokens.get(--tokenIdx);
      }

      return !skipSpace || token.type != Token.TokenType.SPACING && token.type != Token.TokenType.COMMENT && token.type != Token.TokenType.PREPROCESSOR
         ? token
         : null;
   }

   private GLSLParser.TokenNode prevNode(boolean skipSpace) {
      LinkedList<GLSLParser.Node> nodes = this.getNodeStream();
      int idx = nodes.size() - 1;
      if (idx == 0) {
         return null;
      } else {
         GLSLParser.Node node = nodes.get(--idx);
         if (!(node instanceof GLSLParser.TokenNode tokenNode)) {
            return null;
         } else {
            String type = tokenNode.type;

            while (
               skipSpace
                  && idx != 0
                  && (
                     type.equals(Token.TokenType.SPACING.name())
                        || type.equals(Token.TokenType.PREPROCESSOR.name())
                        || type.equals(Token.TokenType.COMMENT.name())
                  )
            ) {
               node = nodes.get(--idx);
               if (!(node instanceof GLSLParser.TokenNode var8)) {
                  return null;
               }

               type = var8.type;
            }

            return !skipSpace
                  || !type.equals(Token.TokenType.SPACING.name())
                     && !type.equals(Token.TokenType.PREPROCESSOR.name())
                     && !type.equals(Token.TokenType.COMMENT.name())
               ? (GLSLParser.TokenNode)node
               : null;
         }
      }
   }

   private void appendToken(Token token) {
      this.appendNode(GLSLParser.TokenNode.fromToken(token));
   }

   private void appendNode(GLSLParser.Node node) {
      this.getNodeStream().add(node);
   }

   private LinkedList<GLSLParser.Node> getNodeStream() {
      return switch (this.stage) {
         case VERTEX -> this.vsStream;
         case FRAGMENT -> this.fsStream;
      };
   }

   public String getOutput(GLSLParser.Stage stage) {
      StringBuilder stringBuilder = new StringBuilder();

      LinkedList<GLSLParser.Node> stream = switch (stage) {
         case VERTEX -> this.vsStream;
         case FRAGMENT -> this.fsStream;
      };
      GLSLParser.Node node = stream.getFirst();
      stringBuilder.append(node.getStringValue());
      stringBuilder.append("\n");
      switch (stage) {
         case VERTEX:
            stringBuilder.append("#define gl_VertexID gl_VertexIndex\n\n");
      }

      stringBuilder.append("#define sampler sampler1\n");
      stringBuilder.append("#define sample sample1\n\n");
      if (!this.legacyUniforms.isEmpty()) {
         stringBuilder.append("layout(binding = 0) uniform UBO {\n");

         for (UniformBlock.Field field : this.legacyUniforms) {
            stringBuilder.append("\t%s %s;\n".formatted(field.type, field.name));
         }

         stringBuilder.append("};\n\n");
      }

      for (int i = 1; i < stream.size(); i++) {
         node = stream.get(i);
         stringBuilder.append(node.getStringValue());
      }

      return stringBuilder.toString();
   }

   public UBO[] createUBOs() {
      if (this.uniformBlockMap.isEmpty()) {
         return new UBO[0];
      }

      int uboCount = this.uniformBlockMap.size();
      int i = 0;
      UBO[] ubos;
      if (!this.legacyUniforms.isEmpty()) {
         uboCount++;
         i = 1;
         ubos = new UBO[uboCount];
         AlignedStruct.Builder builder = new AlignedStruct.Builder();

         for (UniformBlock.Field field : this.legacyUniforms) {
            String name = field.name;
            String type = field.type;
            Uniform.Info uniformInfo = Uniform.createUniformInfo(type, name);
            builder.addUniform(uniformInfo);
         }

         ubos[0] = builder.buildUBO("UBO 0", 0, Integer.MAX_VALUE);
         this.currentUniformLocation++;
      } else {
         ubos = new UBO[uboCount];
      }

      for (UniformBlock uniformBlock : this.uniformBlocks) {
         AlignedStruct.Builder builder = new AlignedStruct.Builder();

         for (UniformBlock.Field field : uniformBlock.fields) {
            String name = field.name;
            String type = field.type;
            Uniform.Info uniformInfo = Uniform.createUniformInfo(type, name);
            builder.addUniform(uniformInfo);
         }

         uniformBlock.setBinding(this.currentUniformLocation++);
         ubos[i] = builder.buildUBO(uniformBlock.name, uniformBlock.binding, Integer.MAX_VALUE);
         i++;
      }

      return ubos;
   }

   public List<ImageDescriptor> getSamplerList() {
      List<ImageDescriptor> imageDescriptors = new ObjectArrayList();
      int imageIdx = 0;

      for (Sampler sampler : this.samplers) {
         int descriptorType = switch (sampler.type) {
            case SAMPLER_2D, SAMPLER_CUBE -> 1;
            case I_SAMPLER_BUFFER -> 4;
         };
         sampler.setBinding(this.currentUniformLocation++);
         imageDescriptors.add(new ImageDescriptor(sampler.binding, "sampler2D", sampler.id, imageIdx, descriptorType));
         imageIdx++;
      }

      return imageDescriptors;
   }

   public interface Node {
      String getStringValue();
   }

   enum PreprocessorState {
      IGNORE,
      DEFAULT;

      PreprocessorState() {
      }
   }

   public enum Stage {
      VERTEX,
      FRAGMENT;

      Stage() {
      }
   }

   enum State {
      LAYOUT,
      UNIFORM,
      UNIFORM_BLOCK,
      ATTRIBUTE,
      DEFAULT;

      State() {
      }
   }

   public static class TokenNode implements GLSLParser.Node {
      String type;
      String value;

      public TokenNode(String type, String value) {
         this.type = type;
         this.value = value;
      }

      public static GLSLParser.TokenNode fromToken(Token token) {
         return new GLSLParser.TokenNode(token.type.name(), token.value);
      }

      @Override
      public String getStringValue() {
         return this.value;
      }

      @Override
      public String toString() {
         return "GenericNode{type='" + this.type + "', value='" + this.value + "'}";
      }
   }
}
